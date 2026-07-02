package doa.ink.workbench.web.api

import doa.ink.workbench.core.common.context.RequestContext
import doa.ink.workbench.core.common.context.TenantRequestContext
import doa.ink.workbench.core.common.errors.AuthenticationFailedException
import doa.ink.workbench.core.common.errors.PermissionDeniedException
import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.core.permission.model.AuthorizationAction
import doa.ink.workbench.core.permission.model.AuthorizationDecision
import doa.ink.workbench.core.permission.model.AuthorizationEnvironment
import doa.ink.workbench.core.permission.model.AuthorizationRequest
import doa.ink.workbench.core.permission.model.AuthorizationResource
import doa.ink.workbench.core.permission.model.AuthorizationSubject
import doa.ink.workbench.core.permission.model.PermissionService
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
  private val clock: Clock,
) {
  private val logger = LoggerFactory.getLogger(javaClass)

  @Around("@annotation(doa.ink.workbench.web.api.Audit)")
  @Suppress("TooGenericExceptionCaught")
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
    } catch (error: RuntimeException) {
      logger.warn(
        "audit_result method={} result=failure durationMs={}",
        joinPoint.signature.toShortString(),
        elapsedMillis(started),
        error,
      )
      throw error
    }
  }

  @Around("@annotation(doa.ink.workbench.web.api.PublishEvent)")
  fun publishEvent(joinPoint: ProceedingJoinPoint): Any? = joinPoint.proceed()

  @Around("@annotation(authorize)")
  fun authorize(joinPoint: ProceedingJoinPoint, authorize: Authorize): Any? {
    val request = authorizationRequest(joinPoint, authorize)
    val decision = runBlocking { permissionService.decide(request) }
    if (decision is AuthorizationDecision.Deny) {
      logger.warn(
        "authorization_denied method={} action={} resource={} tenantId={} reason={}",
        joinPoint.signature.toShortString(),
        request.action.code,
        request.resource.canonical,
        request.tenantId,
        decision.reason.code,
      )
      throw PermissionDeniedException(decision.reason.message)
    }
    return joinPoint.proceed()
  }

  @Around("@annotation(doa.ink.workbench.web.api.RequirePermission)")
  fun rejectLegacyPermission(): Any? {
    throw PermissionDeniedException(
      "Legacy @RequirePermission is disabled. Use the unified authorization model."
    )
  }

  private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started) / 1_000_000

  private fun authorizationRequest(
    joinPoint: ProceedingJoinPoint,
    authorize: Authorize,
  ): AuthorizationRequest {
    val principal = currentPrincipal()
    val tenantContext =
      joinPoint.args.filterIsInstance<TenantRequestContext>().firstOrNull()
        ?: throw PermissionDeniedException("Tenant context is required for authorization.")
    val baseContext =
      joinPoint.args.filterIsInstance<RequestContext>().firstOrNull() ?: tenantContext.base
    val resourceId = annotatedArgument<String>(joinPoint, ResourceId::class.java)
    val projectId = annotatedArgument<UUID>(joinPoint, ResourceProjectId::class.java)
    return AuthorizationRequest(
      subject =
        AuthorizationSubject(
          userId = principal.user.id,
          loginAccountId = principal.loginAccountId,
          credentialType = principal.credentialType,
          credentialId = principal.bearerTokenId ?: principal.sessionId,
          credentialTenantId = principal.tenantId ?: tenantContext.tenantId,
          credentialScopes = principal.credentialScopes,
        ),
      tenantId = tenantContext.tenantId,
      action = AuthorizationAction(authorize.action),
      resource =
        AuthorizationResource(
          type = authorize.resource,
          id = resourceId,
          tenantId = tenantContext.tenantId,
          projectId = projectId,
        ),
      environment =
        AuthorizationEnvironment(
          requestId = baseContext.requestId,
          occurredAt = clock.instant(),
        ),
    )
  }

  private fun currentPrincipal(): AuthenticatedPrincipal =
    SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedPrincipal
      ?: throw AuthenticationFailedException("Authentication required.")

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
