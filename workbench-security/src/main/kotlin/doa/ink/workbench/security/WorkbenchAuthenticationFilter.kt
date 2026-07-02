package doa.ink.workbench.security

import doa.ink.workbench.core.identity.auth.BearerTokenAuthenticator
import doa.ink.workbench.core.identity.auth.SessionAuthenticator
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

const val WORKBENCH_SESSION_COOKIE_NAME = "WORKBENCH_SESSION"

@Component
class WorkbenchAuthenticationFilter(
  private val sessionAuthenticator: SessionAuthenticator,
  private val bearerTokenAuthenticator: BearerTokenAuthenticator,
) : OncePerRequestFilter() {
  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain,
  ) {
    val principal =
      runBlocking {
        bearerToken(request)?.let { bearerTokenAuthenticator.authenticateBearerToken(it) }
          ?: sessionCookie(request)?.let { sessionAuthenticator.authenticateSession(it) }
      }

    if (principal != null) {
      SecurityContextHolder.getContext().authentication =
        UsernamePasswordAuthenticationToken(principal, null, emptyList())
    }

    filterChain.doFilter(request, response)
  }

  private fun bearerToken(request: HttpServletRequest): String? {
    val value = request.getHeader(HttpHeaders.AUTHORIZATION)
    return value
      ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
      ?.substringAfter(" ")
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
  }

  private fun sessionCookie(request: HttpServletRequest): String? =
    request.cookies
      ?.firstOrNull { it.name == WORKBENCH_SESSION_COOKIE_NAME }
      ?.value
      ?.takeIf { it.isNotBlank() }
}
