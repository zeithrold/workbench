package doa.ink.workbench.web.api

import doa.ink.workbench.core.common.context.ApiVersion
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class ApiVersionFilter : OncePerRequestFilter() {
  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain,
  ) {
    val version = request.getHeader(ApiVersion.HeaderName)?.let(::ApiVersion) ?: ApiVersion.Default
    request.setAttribute(ApiVersion::class.java.name, version)
    response.setHeader(ApiVersion.HeaderName, version.value)
    filterChain.doFilter(request, response)
  }
}
