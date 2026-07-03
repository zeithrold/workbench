package ink.doa.workbench.web.api.http

import ink.doa.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders

fun HttpServletRequest.sessionCookieValue(): String? =
  cookies
    ?.firstOrNull { it.name == WORKBENCH_SESSION_COOKIE_NAME }
    ?.value
    ?.takeIf { it.isNotBlank() }

fun HttpServletRequest.bearerTokenValue(): String? {
  val value = getHeader(HttpHeaders.AUTHORIZATION)
  return value
    ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
    ?.substringAfter(" ")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
}

fun HttpServletRequest.defaultRedirectUri(path: String): String {
  val portSuffix = if (isDefaultPort(scheme, serverPort)) "" else ":$serverPort"
  return "$scheme://$serverName$portSuffix$path"
}

private fun isDefaultPort(scheme: String, port: Int): Boolean =
  (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
