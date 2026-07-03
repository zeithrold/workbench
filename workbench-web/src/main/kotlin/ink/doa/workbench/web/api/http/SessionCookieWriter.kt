package ink.doa.workbench.web.api.http

import ink.doa.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import ink.doa.workbench.web.identity.LoginResponse
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

@Component
class SessionCookieWriter(private val clock: Clock) {
  fun loginResponse(response: LoginResponse, sessionSecret: String): ResponseEntity<LoginResponse> =
    ResponseEntity.status(HttpStatus.OK)
      .header(
        HttpHeaders.SET_COOKIE,
        sessionCookie(sessionSecret, response.sessionExpiresAt).toString(),
      )
      .body(response)

  fun <T : Any> createdWithSession(
    body: T,
    sessionSecret: String,
    sessionExpiresAt: OffsetDateTime,
  ): ResponseEntity<T> =
    ResponseEntity.status(HttpStatus.CREATED)
      .header(
        HttpHeaders.SET_COOKIE,
        sessionCookie(sessionSecret, sessionExpiresAt).toString(),
      )
      .body(body)

  fun logoutResponse(): ResponseEntity<Void> =
    ResponseEntity.noContent()
      .header(HttpHeaders.SET_COOKIE, expiredSessionCookie().toString())
      .build()

  private fun sessionCookie(secret: String, expiresAt: OffsetDateTime): ResponseCookie {
    val maxAge = Duration.between(OffsetDateTime.now(clock), expiresAt).coerceAtLeast(Duration.ZERO)
    return ResponseCookie.from(WORKBENCH_SESSION_COOKIE_NAME, secret)
      .httpOnly(true)
      .secure(true)
      .sameSite("Lax")
      .path("/")
      .maxAge(maxAge)
      .build()
  }

  private fun expiredSessionCookie(): ResponseCookie =
    ResponseCookie.from(WORKBENCH_SESSION_COOKIE_NAME, "")
      .httpOnly(true)
      .secure(true)
      .sameSite("Lax")
      .path("/")
      .maxAge(Duration.ZERO)
      .build()
}
