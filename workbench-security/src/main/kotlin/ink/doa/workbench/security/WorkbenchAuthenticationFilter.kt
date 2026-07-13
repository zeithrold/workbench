package ink.doa.workbench.security

import ink.doa.workbench.identity.auth.BearerTokenAuthenticator
import ink.doa.workbench.identity.auth.SessionAuthenticator
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

const val WORKBENCH_SESSION_COOKIE_NAME = "WORKBENCH_SESSION"

@Component
@ConditionalOnWebApplication
class WorkbenchAuthenticationFilter(
  private val sessionAuthenticator: SessionAuthenticator,
  private val bearerTokenAuthenticator: BearerTokenAuthenticator,
) : OncePerRequestFilter() {
  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain,
  ) {
    val bearerToken = bearerToken(request)
    val principal = runBlocking {
      if (bearerToken != null) {
        bearerTokenAuthenticator.authenticateBearerToken(bearerToken)
      } else {
        sessionCookie(request)?.let { sessionAuthenticator.authenticateSession(it) }
      }
    }

    if (bearerToken != null && principal == null) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
      return
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
