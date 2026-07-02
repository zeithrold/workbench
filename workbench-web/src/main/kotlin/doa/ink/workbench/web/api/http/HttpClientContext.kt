package doa.ink.workbench.web.api.http

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders

data class HttpClientContext(
  val ipAddress: String?,
  val userAgent: String?,
) {
  companion object {
    fun from(request: HttpServletRequest): HttpClientContext =
      HttpClientContext(
        ipAddress = request.remoteAddr,
        userAgent = request.getHeader(HttpHeaders.USER_AGENT),
      )
  }
}

fun HttpClientContext.toServiceContext(): doa.ink.workbench.service.identity.ClientContext =
  doa.ink.workbench.service.identity.ClientContext(
    ipAddress = ipAddress,
    userAgent = userAgent,
  )
