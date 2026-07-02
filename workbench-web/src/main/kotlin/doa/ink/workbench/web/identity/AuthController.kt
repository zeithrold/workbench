package doa.ink.workbench.web.identity

import doa.ink.workbench.core.common.errors.AuthenticationFailedException
import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.core.identity.model.PasswordLoginCommand
import doa.ink.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import doa.ink.workbench.service.identity.auth.AuthenticationService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
  private val authenticationService: AuthenticationService,
  private val clock: Clock,
) {
  @PostMapping("/login")
  suspend fun login(
    @Valid @RequestBody request: PasswordLoginRequest,
    servletRequest: HttpServletRequest,
  ): ResponseEntity<LoginResponse> {
    val result =
      authenticationService.loginWithPassword(
        PasswordLoginCommand(
          tenantId = request.tenantId,
          subject = request.subject,
          password = request.password,
          issueBearerToken = request.issueBearerToken,
          ipAddress = servletRequest.remoteAddr,
          userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT),
        )
      )

    val cookie = sessionCookie(result.session.secret, result.session.expiresAt)
    return ResponseEntity.status(HttpStatus.OK)
      .header(HttpHeaders.SET_COOKIE, cookie.toString())
      .body(
        LoginResponse(
          user =
            AuthenticatedUserResponse(
              id = result.principal.user.id,
              apiId = result.principal.user.apiId.value,
              displayName = result.principal.user.displayName,
              primaryEmail = result.principal.user.primaryEmail,
            ),
          sessionExpiresAt = result.session.expiresAt,
          bearerToken =
            result.bearerToken?.let {
              IssuedTokenResponse(id = it.id, token = it.secret, expiresAt = it.expiresAt)
            },
        )
      )
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  suspend fun logout(servletRequest: HttpServletRequest): ResponseEntity<Void> {
    sessionCookieValue(servletRequest)?.let {
      authenticationService.logoutSession(
        sessionSecret = it,
        ipAddress = servletRequest.remoteAddr,
        userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT),
      )
    }
    bearerToken(servletRequest)?.let {
      authenticationService.revokeBearerToken(
        tokenSecret = it,
        ipAddress = servletRequest.remoteAddr,
        userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT),
      )
    }
    return ResponseEntity.noContent()
      .header(HttpHeaders.SET_COOKIE, expiredSessionCookie().toString())
      .build()
  }

  @PostMapping("/tokens")
  suspend fun createToken(servletRequest: HttpServletRequest): IssuedTokenResponse {
    val principal = currentPrincipal()
    val loginAccountId =
      principal.loginAccountId ?: throw AuthenticationFailedException("Authentication required.")
    val token =
      authenticationService.createBearerToken(
        userId = principal.user.id,
        loginAccountId = loginAccountId,
        ipAddress = servletRequest.remoteAddr,
        userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT),
      )
    return IssuedTokenResponse(id = token.id, token = token.secret, expiresAt = token.expiresAt)
  }

  @DeleteMapping("/tokens/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  suspend fun revokeToken(
    @PathVariable id: UUID,
    servletRequest: HttpServletRequest,
  ) {
    authenticationService.revokeBearerTokenById(
      tokenId = id,
      actorUserId = currentPrincipal().user.id,
      ipAddress = servletRequest.remoteAddr,
      userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT),
    )
  }

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

  private fun currentPrincipal(): AuthenticatedPrincipal =
    SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedPrincipal
      ?: throw AuthenticationFailedException("Authentication required.")

  private fun sessionCookieValue(request: HttpServletRequest): String? =
    request.cookies
      ?.firstOrNull { it.name == WORKBENCH_SESSION_COOKIE_NAME }
      ?.value
      ?.takeIf { it.isNotBlank() }

  private fun bearerToken(request: HttpServletRequest): String? {
    val value = request.getHeader(HttpHeaders.AUTHORIZATION)
    return value
      ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
      ?.substringAfter(" ")
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
  }
}

data class PasswordLoginRequest(
  val tenantId: UUID,
  @field:NotBlank val subject: String,
  @field:NotBlank val password: String,
  val issueBearerToken: Boolean = false,
)

data class LoginResponse(
  val user: AuthenticatedUserResponse,
  val sessionExpiresAt: OffsetDateTime,
  val bearerToken: IssuedTokenResponse?,
)

data class AuthenticatedUserResponse(
  val id: UUID,
  val apiId: String,
  val displayName: String,
  val primaryEmail: String?,
)

data class IssuedTokenResponse(
  val id: UUID,
  val token: String,
  val expiresAt: OffsetDateTime,
)
