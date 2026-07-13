package ink.doa.workbench.web.api

import ink.doa.workbench.identity.model.AuthenticatedPrincipal
import ink.doa.workbench.kernel.common.errors.AuthenticationFailedException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class AuthenticatedPrincipalResolver : HandlerMethodArgumentResolver {
  override fun supportsParameter(parameter: MethodParameter): Boolean =
    parameter.parameterType == AuthenticatedPrincipal::class.java

  override fun resolveArgument(
    parameter: MethodParameter,
    mavContainer: ModelAndViewContainer?,
    webRequest: NativeWebRequest,
    binderFactory: WebDataBinderFactory?,
  ): Any =
    SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedPrincipal
      ?: throw AuthenticationFailedException(WorkbenchErrorCode.AUTH_AUTHENTICATION_REQUIRED)
}
