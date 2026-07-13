package ink.doa.workbench.web.api

import ink.doa.workbench.identity.model.AuthenticatedPrincipal
import ink.doa.workbench.identity.permission.AuthorizationResourceAttributeResolver
import ink.doa.workbench.identity.permission.model.AuthorizationAction
import ink.doa.workbench.identity.permission.model.AuthorizationDecision
import ink.doa.workbench.identity.permission.model.AuthorizationEnvironment
import ink.doa.workbench.identity.permission.model.AuthorizationRequest
import ink.doa.workbench.identity.permission.model.AuthorizationResource
import ink.doa.workbench.identity.permission.model.AuthorizationScope
import ink.doa.workbench.identity.permission.model.AuthorizationSubject
import ink.doa.workbench.identity.permission.model.PermissionService
import ink.doa.workbench.kernel.common.errors.AuthenticationFailedException
import ink.doa.workbench.kernel.common.errors.PermissionDeniedException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.tenant.tenant.TenantOperationalGuard
import ink.doa.workbench.web.api.context.InstanceRequestContext
import ink.doa.workbench.web.api.context.ProjectRequestContext
import ink.doa.workbench.web.api.context.ScopedRequestContext
import ink.doa.workbench.web.api.context.TenantRequestContext
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Aspect
@Component
class InfrastructureAspect(
  private val permissionService: PermissionService,
  private val tenantOperationalGuard: TenantOperationalGuard,
  private val attributeResolvers: List<AuthorizationResourceAttributeResolver>,
  private val clock: Clock,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  @Around("@annotation(ink.doa.workbench.web.api.Audit)")
  fun audit(joinPoint: ProceedingJoinPoint): Any? {
    val started = System.nanoTime()
    return try {
      joinPoint.proceed().also {
        logger.info(
          "audit_result method={} result=success durationMs={}",
          joinPoint.signature.toShortString(),
          elapsedMillis(started),
        )
      }
    } catch (
      // AOP boundary: audit every application failure before rethrowing the original exception.
      @Suppress("TooGenericExceptionCaught") error: RuntimeException) {
      logger.warn(
        "audit_result method={} result=failure durationMs={}",
        joinPoint.signature.toShortString(),
        elapsedMillis(started),
        error,
      )
      throw error
    }
  }

  @Around("@annotation(ink.doa.workbench.web.api.PublishEvent)")
  @Deprecated("Use DomainEventPublisher.publish(spec, payload) explicitly in services.")
  fun publishEvent(joinPoint: ProceedingJoinPoint): Any? = joinPoint.proceed()

  @Around("@annotation(authorize)")
  fun authorize(joinPoint: ProceedingJoinPoint, authorize: Authorize): Any? {
    authorizeAction(joinPoint, authorize.action, authorize.resource)
    return joinPoint.proceed()
  }

  @Around("@annotation(authorizeAll)")
  fun authorizeAll(joinPoint: ProceedingJoinPoint, authorizeAll: AuthorizeAll): Any? {
    authorizeAll.actions.forEach { action ->
      authorizeAction(joinPoint, action, authorizeAll.resource)
    }
    return joinPoint.proceed()
  }

  private fun authorizeAction(
    joinPoint: ProceedingJoinPoint,
    action: String,
    resource: String,
  ) {
    val projectContext = joinPoint.args.filterIsInstance<ProjectRequestContext>().firstOrNull()
    val tenantContext = joinPoint.args.filterIsInstance<TenantRequestContext>().firstOrNull()
    val tenantId = tenantContext?.tenant?.id ?: projectContext?.tenant?.id
    tenantId?.let { id ->
      runBlocking { tenantOperationalGuard.ensureOperational(id) }
    }
    val request = runBlocking {
      enrichAuthorizationRequest(authorizationRequest(joinPoint, action, resource))
    }
    val decision = runBlocking { permissionService.decide(request) }
    if (decision is AuthorizationDecision.Deny) {
      logger.warn(
        "authorization_denied method={} action={} resource={} scope={} tenantId={} reason={}",
        joinPoint.signature.toShortString(),
        request.action.code,
        request.resource.canonical,
        request.scope,
        request.tenantId,
        decision.reason.code,
      )
      throw PermissionDeniedException(
        WorkbenchErrorCode.fromAuthorizationReason(decision.reason.code),
        decision.reason.message,
      )
    }
  }

  @Around("@annotation(ink.doa.workbench.web.api.RequirePermission)")
  fun rejectLegacyPermission(): Any? {
    throw PermissionDeniedException(WorkbenchErrorCode.AUTH_PERMISSION_LEGACY_DISABLED)
  }

  private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started) / 1_000_000

  private fun authorizationRequest(
    joinPoint: ProceedingJoinPoint,
    action: String,
    resource: String,
  ): AuthorizationRequest {
    val principal = currentPrincipal()
    val projectContext = joinPoint.args.filterIsInstance<ProjectRequestContext>().firstOrNull()
    val tenantContext = joinPoint.args.filterIsInstance<TenantRequestContext>().firstOrNull()
    val instanceContext = joinPoint.args.filterIsInstance<InstanceRequestContext>().firstOrNull()
    val scope =
      when {
        tenantContext != null || projectContext != null -> AuthorizationScope.TENANT
        instanceContext != null -> AuthorizationScope.INSTANCE
        else -> throw PermissionDeniedException(WorkbenchErrorCode.AUTH_PERMISSION_CONTEXT_REQUIRED)
      }
    val scopedContext =
      joinPoint.args.filterIsInstance<ScopedRequestContext>().firstOrNull()
        ?: throw PermissionDeniedException(WorkbenchErrorCode.AUTH_PERMISSION_CONTEXT_REQUIRED)
    val resourceId = annotatedArgument<String>(joinPoint, ResourceId::class.java)
    val annotatedProjectId = annotatedArgument<UUID>(joinPoint, ResourceProjectId::class.java)
    val tenantId = tenantContext?.tenant?.id ?: projectContext?.tenant?.id
    val projectId = projectContext?.project?.id ?: annotatedProjectId
    val resourcePublicId = resourceId ?: projectContext?.project?.publicId?.value
    return AuthorizationRequest(
      scope = scope,
      subject =
        AuthorizationSubject(
          userId = principal.user.id,
          userApiId = principal.user.apiId.value,
          loginAccountId = principal.loginAccountId,
          credentialType = principal.credentialType,
          credentialId = principal.bearerTokenId ?: principal.sessionId,
          credentialTenantId = principal.tenantId ?: tenantId,
          credentialScopes = principal.credentialScopes,
        ),
      tenantId = tenantId,
      action = AuthorizationAction(action),
      resource =
        AuthorizationResource(
          type = resource,
          id = resourcePublicId,
          tenantId = tenantId,
          projectId = projectId,
        ),
      environment =
        AuthorizationEnvironment(
          requestId = scopedContext.requestId,
          occurredAt = clock.instant(),
        ),
    )
  }

  private suspend fun enrichAuthorizationRequest(
    request: AuthorizationRequest
  ): AuthorizationRequest {
    val actorAttributes =
      mapOf(
        "actor" to request.subject.userApiId,
        "actorId" to request.subject.userApiId,
      )
    val resolvedAttributes =
      attributeResolvers
        .firstOrNull { it.supports(request.resource) }
        ?.resolveAttributes(request)
        .orEmpty()
    val attributes = actorAttributes + resolvedAttributes
    if (attributes == request.resource.attributes) return request
    return request.copy(resource = request.resource.copy(attributes = attributes))
  }

  private fun currentPrincipal(): AuthenticatedPrincipal =
    SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedPrincipal
      ?: throw AuthenticationFailedException(WorkbenchErrorCode.AUTH_AUTHENTICATION_REQUIRED)

  private inline fun <reified T> annotatedArgument(
    joinPoint: ProceedingJoinPoint,
    annotation: Class<out Annotation>,
  ): T? {
    val signature = joinPoint.signature as? MethodSignature ?: return null
    return signature.method.parameters
      .mapIndexedNotNull { index, parameter ->
        if (parameter.isAnnotationPresent(annotation)) joinPoint.args.getOrNull(index) else null
      }
      .filterIsInstance<T>()
      .firstOrNull()
  }
}
