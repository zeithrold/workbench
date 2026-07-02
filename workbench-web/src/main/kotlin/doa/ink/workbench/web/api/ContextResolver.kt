package doa.ink.workbench.web.api

import doa.ink.workbench.core.common.context.ApiVersion
import doa.ink.workbench.core.common.context.RequestContext
import java.time.Clock
import java.util.UUID
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
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
  ): Any {
    val version =
      webRequest.getAttribute(ApiVersion::class.java.name, NativeWebRequest.SCOPE_REQUEST)
        as? ApiVersion ?: ApiVersion.Default
    return RequestContext(
      requestId = webRequest.getHeader("X-Request-Id") ?: UUID.randomUUID().toString(),
      apiVersion = version,
      actorUserId = null,
      actorPublicId = null,
      receivedAt = clock.instant(),
    )
  }
}

@Configuration
class WebMvcContextConfiguration(private val resolver: RequestContextResolver) : WebMvcConfigurer {
  override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
    resolvers.add(resolver)
  }
}
