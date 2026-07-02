package doa.ink.workbench.web.api

import doa.ink.workbench.core.common.context.RequestContext
import doa.ink.workbench.core.common.context.TenantRequestContext
import doa.ink.workbench.core.common.errors.AuthenticationFailedException
import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.service.identity.SessionService
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
    val base =
      requestContextResolver.resolveArgument(parameter, mavContainer, webRequest, binderFactory)
        as RequestContext
    val principal = currentPrincipal()
    val tenant = runBlocking { sessionService.requireActiveTenant(principal) }
    return TenantRequestContext(
      base = base,
      tenantId = tenant.id,
      tenantPublicId = tenant.apiId,
    )
  }

  private fun currentPrincipal(): AuthenticatedPrincipal =
    SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedPrincipal
      ?: throw AuthenticationFailedException("Authentication required.")
}
