package one.ztd.workbench.web.api

import one.ztd.workbench.tenant.instance.InstanceContextProvider
import one.ztd.workbench.web.api.context.InstanceRequestContext
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class InstanceRequestContextResolver(
  private val requestContextResolver: RequestContextResolver,
  private val instanceContextProvider: InstanceContextProvider,
) : HandlerMethodArgumentResolver {
  override fun supportsParameter(parameter: MethodParameter): Boolean =
    parameter.parameterType == InstanceRequestContext::class.java

  override fun resolveArgument(
    parameter: MethodParameter,
    mavContainer: ModelAndViewContainer?,
    webRequest: NativeWebRequest,
    binderFactory: WebDataBinderFactory?,
  ): Any {
    val base = requestContextResolver.resolveBase(webRequest)
    return InstanceRequestContext(
      requestId = base.requestId,
      apiVersion = base.apiVersion,
      actor = base.actor,
      receivedAt = base.receivedAt,
      instance = instanceContextProvider.current(),
    )
  }
}
