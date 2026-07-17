package one.ztd.workbench.web.api.http

import jakarta.servlet.http.HttpServletRequest
import one.ztd.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
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
