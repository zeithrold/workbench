package doa.ink.workbench.web.api

import doa.ink.workbench.core.common.context.TenantContextSummary
import doa.ink.workbench.core.common.context.TenantRequestContext
import doa.ink.workbench.core.common.errors.AuthenticationFailedException
import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.security.identity.SessionService
import doa.ink.workbench.tenant.instance.InstanceContextProvider
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
      ?: throw AuthenticationFailedException("Authentication required.")
}
