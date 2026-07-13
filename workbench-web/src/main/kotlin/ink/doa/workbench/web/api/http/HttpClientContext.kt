package ink.doa.workbench.web.api.http

import ink.doa.workbench.kernel.common.context.RequestHost
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders

data class HttpClientContext(
  val ipAddress: String?,
  val userAgent: String?,
  val requestHost: RequestHost?,
) {
  companion object {
    fun from(request: HttpServletRequest): HttpClientContext =
      HttpClientContext(
        ipAddress = request.remoteAddr,
        userAgent = request.getHeader(HttpHeaders.USER_AGENT),
        requestHost = resolveRequestHost(request),
      )

    fun resolveRequestHost(request: HttpServletRequest): RequestHost {
      val scheme = resolveScheme(request)
      val host =
        request.getHeader("X-Forwarded-Host")?.substringBefore(',')?.trim()?.ifBlank { null }
          ?: formatHost(request.serverName, request.serverPort, scheme)
      return RequestHost(scheme = scheme, host = host)
    }

    private fun resolveScheme(request: HttpServletRequest): String =
      request.getHeader("X-Forwarded-Proto")?.substringBefore(',')?.trim()?.ifBlank { null }
        ?: if (request.getHeader("X-Forwarded-Ssl") == "on") "https" else request.scheme

    private fun formatHost(name: String, port: Int, scheme: String): String =
      if (isDefaultPort(scheme, port)) name else "$name:$port"

    private fun isDefaultPort(scheme: String, port: Int): Boolean =
      (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
  }
}

fun HttpClientContext.toServiceContext(): ink.doa.workbench.identity.ClientContext =
  ink.doa.workbench.identity.ClientContext(
    ipAddress = ipAddress,
    userAgent = userAgent,
  )
