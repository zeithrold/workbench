package doa.ink.workbench.web.identity

import doa.ink.workbench.core.common.errors.AuthenticationFailedException
import doa.ink.workbench.core.identity.LoginAccountRepository
import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.core.identity.model.LoginCommand
import doa.ink.workbench.core.identity.model.LoginMethodKind
import doa.ink.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import doa.ink.workbench.service.identity.MembershipService
import doa.ink.workbench.service.identity.SessionService
import doa.ink.workbench.service.identity.auth.AuthenticationService
import doa.ink.workbench.service.identity.auth.FederatedAuthService
import doa.ink.workbench.service.identity.auth.MagicLinkAuthService
import doa.ink.workbench.service.identity.auth.normalizeSubject
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
@Suppress("TooManyFunctions")
class AuthController(
  private val authenticationService: AuthenticationService,
  private val sessionService: SessionService,
  private val membershipService: MembershipService,
  private val loginAccounts: LoginAccountRepository,
  private val federatedAuthService: FederatedAuthService,
  private val magicLinkAuthService: MagicLinkAuthService,
  private val clock: Clock,
) {
  @PostMapping("/login")
  suspend fun login(
    @Valid @RequestBody request: LoginRequest,
    servletRequest: HttpServletRequest,
  ): ResponseEntity<LoginResponse> {
    val result =
      authenticationService.login(
        LoginCommand(
          method = request.method,
          loginMethodCode = request.loginMethodCode,
          tenantApiId = request.tenantApiId,
          subject = request.subject,
          password = request.password,
          token = request.token,
          email = request.email,
          issueBearerToken = request.issueBearerToken,
          ipAddress = servletRequest.remoteAddr,
          userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT),
        )
      )
    return loginResponse(result)
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

  @GetMapping("/memberships")
  suspend fun memberships(): List<MembershipResponse> {
    val principal = currentPrincipal()
    return membershipService.listActiveMemberships(principal.user.id).map {
      MembershipResponse.from(it)
    }
  }

  @GetMapping("/login-options")
  suspend fun loginOptions(@RequestParam identifier: String): List<LoginOptionResponse> =
    loginAccounts.listLoginOptionsForIdentifier(normalizeSubject(identifier)).map {
      LoginOptionResponse(
        tenantApiId = it.tenantApiId,
        tenantName = it.tenantName,
        loginMethodCode = it.loginMethodCode,
        loginMethodKind = it.loginMethodKind,
        loginMethodName = it.loginMethodName,
      )
    }

  @PostMapping("/tokens")
  suspend fun createToken(
    @Valid @RequestBody request: CreateTokenRequest,
    servletRequest: HttpServletRequest,
  ): IssuedTokenResponse {
    val principal = currentPrincipal()
    val loginAccountId =
      principal.loginAccountId ?: throw AuthenticationFailedException("Authentication required.")
    val tenantId = request.tenantId ?: sessionService.requireActiveTenantId(principal)
    val token =
      authenticationService.createBearerToken(
        userId = principal.user.id,
        loginAccountId = loginAccountId,
        tenantId = tenantId,
        name = request.name,
        scopes = request.scopes.toSet(),
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

  @PostMapping("/federated/authorize")
  suspend fun federatedAuthorize(
    @Valid @RequestBody request: FederatedAuthorizeRequest,
    servletRequest: HttpServletRequest,
  ): FederatedAuthorizeResponse {
    val redirectUri =
      request.redirectUri ?: defaultRedirectUri(servletRequest, "/api/auth/oauth2/callback")
    val result =
      federatedAuthService.beginAuthorize(
        loginMethodCode = request.loginMethodCode,
        tenantApiId = request.tenantApiId,
        returnUrl = request.returnUrl,
        redirectUri = redirectUri,
      )
    return FederatedAuthorizeResponse(
      authorizationUrl = result.authorizationUrl,
      state = result.state,
    )
  }

  @GetMapping("/oauth2/callback")
  suspend fun oauthCallback(
    @RequestParam code: String,
    @RequestParam state: String,
    servletRequest: HttpServletRequest,
  ): ResponseEntity<LoginResponse> {
    val redirectUri = defaultRedirectUri(servletRequest, "/api/auth/oauth2/callback")
    val identity = federatedAuthService.completeOAuthCallback(code, state, redirectUri)
    val result =
      authenticationService.completeLogin(
        identity = identity,
        issueBearerToken = false,
        ipAddress = servletRequest.remoteAddr,
        userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT),
      )
    return loginResponse(result)
  }

  @PostMapping("/saml2/acs")
  suspend fun samlAcs(
    @RequestParam("SAMLResponse") samlResponse: String,
    @RequestParam("RelayState") relayState: String,
    servletRequest: HttpServletRequest,
  ): ResponseEntity<LoginResponse> {
    val identity = federatedAuthService.completeSamlAcs(samlResponse, relayState)
    val result =
      authenticationService.completeLogin(
        identity = identity,
        issueBearerToken = false,
        ipAddress = servletRequest.remoteAddr,
        userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT),
      )
    return loginResponse(result)
  }

  @PostMapping("/magic-link/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun requestMagicLink(@Valid @RequestBody request: MagicLinkRequest) {
    magicLinkAuthService.requestMagicLink(
      email = request.email,
      tenantApiId = request.tenantApiId,
      loginMethodCode = request.loginMethodCode,
    )
  }

  @GetMapping("/magic-link/verify")
  suspend fun verifyMagicLink(
    @RequestParam token: String,
    servletRequest: HttpServletRequest,
  ): ResponseEntity<LoginResponse> {
    val identity = magicLinkAuthService.resolveToken(token)
    val result =
      authenticationService.completeLogin(
        identity =
          identity.let {
            doa.ink.workbench.core.identity.model.AuthenticatedIdentity(
              user = it.user,
              loginAccount = it.loginAccount,
            )
          },
        issueBearerToken = false,
        ipAddress = servletRequest.remoteAddr,
        userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT),
        tenantIdForAudit = null,
      )
    return loginResponse(result)
  }

  private fun loginResponse(
    result: doa.ink.workbench.core.identity.model.AuthenticationResult
  ): ResponseEntity<LoginResponse> {
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

  private fun defaultRedirectUri(request: HttpServletRequest, path: String): String {
    val scheme = request.scheme
    val host = request.serverName
    val port = request.serverPort
    val portSuffix = if (isDefaultPort(scheme, port)) "" else ":$port"
    return "$scheme://$host$portSuffix$path"
  }

  private fun isDefaultPort(scheme: String, port: Int): Boolean =
    (scheme == "http" && port == 80) || (scheme == "https" && port == 443)

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

data class LoginRequest(
  val method: LoginMethodKind,
  val loginMethodCode: String? = null,
  val tenantApiId: String? = null,
  val subject: String? = null,
  val password: String? = null,
  val token: String? = null,
  val email: String? = null,
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

data class CreateTokenRequest(
  val tenantId: UUID? = null,
  val name: String? = null,
  val scopes: List<String> = listOf("workbench.api"),
)

data class MembershipResponse(
  val tenantApiId: String,
  val tenantName: String,
  val tenantSlug: String,
  val membershipApiId: String,
) {
  companion object {
    fun from(view: doa.ink.workbench.service.identity.TenantMembershipView) =
      MembershipResponse(
        tenantApiId = view.tenant.apiId.value,
        tenantName = view.tenant.name,
        tenantSlug = view.tenant.slug,
        membershipApiId = view.membership.apiId.value,
      )
  }
}

data class LoginOptionResponse(
  val tenantApiId: String,
  val tenantName: String,
  val loginMethodCode: String,
  val loginMethodKind: LoginMethodKind,
  val loginMethodName: String,
)

data class FederatedAuthorizeRequest(
  @field:NotBlank val loginMethodCode: String,
  @field:NotBlank val tenantApiId: String,
  @field:NotBlank val returnUrl: String,
  val redirectUri: String? = null,
)

data class FederatedAuthorizeResponse(
  val authorizationUrl: String,
  val state: String,
)

data class MagicLinkRequest(
  @field:NotBlank val email: String,
  @field:NotBlank val tenantApiId: String,
  @field:NotBlank val loginMethodCode: String,
)
