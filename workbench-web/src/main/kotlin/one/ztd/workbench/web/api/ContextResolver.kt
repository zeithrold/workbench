package one.ztd.workbench.web.api

import java.time.Clock
import java.util.UUID
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.web.api.context.ApiVersion
import one.ztd.workbench.web.api.context.RequestContext
import one.ztd.workbench.web.api.context.UserContextSummary
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Component
class RequestContextResolver(private val clock: Clock) : HandlerMethodArgumentResolver {
  override fun supportsParameter(parameter: MethodParameter): Boolean =
    parameter.parameterType == RequestContext::class.java

  override fun resolveArgument(
    parameter: MethodParameter,
    mavContainer: ModelAndViewContainer?,
    webRequest: NativeWebRequest,
    binderFactory: WebDataBinderFactory?,
  ): Any = resolveBase(webRequest)

  fun resolveBase(webRequest: NativeWebRequest): RequestContext {
    val version =
      webRequest.getAttribute(ApiVersion::class.java.name, NativeWebRequest.SCOPE_REQUEST)
        as? ApiVersion ?: ApiVersion.Default
    val principal =
      SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedPrincipal
    return RequestContext(
      requestId = webRequest.getHeader("X-Request-Id") ?: UUID.randomUUID().toString(),
      apiVersion = version,
      actor = principal?.user?.let(UserContextSummary::from),
      receivedAt = clock.instant(),
    )
  }
}

@Configuration
class WebMvcContextConfiguration(
  private val requestContextResolver: RequestContextResolver,
  private val tenantRequestContextResolver: TenantRequestContextResolver,
  private val projectRequestContextResolver: ProjectRequestContextResolver,
  private val instanceRequestContextResolver: InstanceRequestContextResolver,
  private val authenticatedPrincipalResolver: AuthenticatedPrincipalResolver,
) : WebMvcConfigurer {
  override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
    resolvers.add(requestContextResolver)
    resolvers.add(tenantRequestContextResolver)
    resolvers.add(projectRequestContextResolver)
    resolvers.add(instanceRequestContextResolver)
    resolvers.add(authenticatedPrincipalResolver)
  }
}
