package ink.doa.workbench.web.api

import ink.doa.workbench.identity.SessionService
import ink.doa.workbench.identity.model.AuthenticatedPrincipal
import ink.doa.workbench.kernel.common.errors.AuthenticationFailedException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.tenant.instance.InstanceContextProvider
import ink.doa.workbench.web.api.context.TenantContextSummary
import ink.doa.workbench.web.api.context.TenantRequestContext
import kotlinx.coroutines.runBlocking
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class TenantRequestContextResolver(
  private val requestContextResolver: RequestContextResolver,
  private val instanceContextProvider: InstanceContextProvider,
  private val sessionService: SessionService,
) : HandlerMethodArgumentResolver {
  override fun supportsParameter(parameter: MethodParameter): Boolean =
    parameter.parameterType == TenantRequestContext::class.java

  override fun resolveArgument(
    parameter: MethodParameter,
    mavContainer: ModelAndViewContainer?,
    webRequest: NativeWebRequest,
    binderFactory: WebDataBinderFactory?,
  ): Any {
    val base = requestContextResolver.resolveBase(webRequest)
    val principal = currentPrincipal()
    val tenant = runBlocking { sessionService.requireActiveTenant(principal) }
    return TenantRequestContext(
      requestId = base.requestId,
      apiVersion = base.apiVersion,
      actor = base.actor,
      receivedAt = base.receivedAt,
      instance = instanceContextProvider.current(),
      tenant = TenantContextSummary.from(tenant),
    )
  }

  private fun currentPrincipal(): AuthenticatedPrincipal =
    SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedPrincipal
      ?: throw AuthenticationFailedException(WorkbenchErrorCode.AUTH_AUTHENTICATION_REQUIRED)
}
