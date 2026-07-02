package doa.ink.workbench.web.identity

import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.service.identity.AuthApplicationService
import doa.ink.workbench.web.api.SessionSecured
import doa.ink.workbench.web.api.StandardErrorResponses
import doa.ink.workbench.web.api.http.HttpClientContext
import doa.ink.workbench.web.api.http.SessionCookieWriter
import doa.ink.workbench.web.api.http.bearerTokenValue
import doa.ink.workbench.web.api.http.defaultRedirectUri
import doa.ink.workbench.web.api.http.sessionCookieValue
import doa.ink.workbench.web.api.http.toServiceContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
@Tag(name = "Auth", description = "Authentication and session protocol")
@StandardErrorResponses
class AuthController(
  private val authApplicationService: AuthApplicationService,
  private val sessionCookieWriter: SessionCookieWriter,
) {
  @PostMapping("/login")
  @Operation(
    summary = "Sign in",
    description = "Authenticates the user and sets the session cookie.",
  )
  suspend fun login(
    @Valid @RequestBody request: LoginRequest,
    servletRequest: HttpServletRequest,
  ): ResponseEntity<LoginResponse> {
    val client = HttpClientContext.from(servletRequest).toServiceContext()
    val view = authApplicationService.login(request.toCommand(client))
    return sessionCookieWriter.loginResponse(LoginResponse.from(view), view.sessionSecret)
  }

  @PostMapping("/logout")
  @Operation(
    summary = "Sign out",
    description = "Revokes the active session or bearer token and clears the session cookie.",
  )
  suspend fun logout(servletRequest: HttpServletRequest): ResponseEntity<Void> {
    val client = HttpClientContext.from(servletRequest).toServiceContext()
    authApplicationService.logout(
      client = client,
      sessionSecret = servletRequest.sessionCookieValue(),
      bearerToken = servletRequest.bearerTokenValue(),
    )
    return sessionCookieWriter.logoutResponse()
  }

  @GetMapping("/memberships")
  @SessionSecured
  @Operation(
    summary = "List memberships",
    description = "Lists tenant memberships for the authenticated user.",
  )
  suspend fun memberships(principal: AuthenticatedPrincipal): List<MembershipResponse> =
    authApplicationService.listMemberships(principal).map { MembershipResponse.from(it) }

  @GetMapping("/login-options")
  @Operation(
    summary = "List login options",
    description = "Discovers tenant and login method options for an identifier.",
  )
  suspend fun loginOptions(@RequestParam identifier: String): List<LoginOptionResponse> =
    authApplicationService.listLoginOptions(identifier).map { LoginOptionResponse.from(it) }

  @PostMapping("/tokens")
  @SessionSecured
  @Operation(
    summary = "Issue bearer token",
    description = "Creates a long-lived bearer token for API access.",
  )
  suspend fun createToken(
    @Valid @RequestBody request: CreateTokenRequest,
    principal: AuthenticatedPrincipal,
    servletRequest: HttpServletRequest,
  ): IssuedTokenResponse {
    val client = HttpClientContext.from(servletRequest).toServiceContext()
    return IssuedTokenResponse.from(
      authApplicationService.issueBearerToken(
        principal = principal,
        tenantId = request.tenantId,
        name = request.name,
        scopes = request.scopes,
        client = client,
      )
    )
  }

  @DeleteMapping("/tokens/{id}")
  @SessionSecured
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Revoke bearer token", description = "Revokes a bearer token by public id.")
  suspend fun revokeToken(
    @PathVariable id: String,
    principal: AuthenticatedPrincipal,
    servletRequest: HttpServletRequest,
  ) {
    val client = HttpClientContext.from(servletRequest).toServiceContext()
    authApplicationService.revokeBearerToken(principal, id, client)
  }

  @PostMapping("/federated/authorize")
  @Operation(
    summary = "Start federated login",
    description = "Begins OAuth or SAML authorization for the given tenant and login method.",
  )
  suspend fun federatedAuthorize(
    @Valid @RequestBody request: FederatedAuthorizeRequest,
    servletRequest: HttpServletRequest,
  ): FederatedAuthorizeResponse {
    val redirectUri =
      request.redirectUri ?: servletRequest.defaultRedirectUri("/api/auth/oauth2/callback")
    return FederatedAuthorizeResponse.from(
      authApplicationService.beginFederatedAuthorize(
        loginMethodId = request.loginMethodId,
        tenantId = request.tenantId,
        returnUrl = request.returnUrl,
        redirectUri = redirectUri,
      )
    )
  }

  @GetMapping("/oauth2/callback")
  @Operation(
    summary = "OAuth callback",
    description = "Completes OAuth login after provider redirect.",
  )
  suspend fun oauthCallback(
    @RequestParam code: String,
    @RequestParam state: String,
    servletRequest: HttpServletRequest,
  ): ResponseEntity<LoginResponse> {
    val client = HttpClientContext.from(servletRequest).toServiceContext()
    val redirectUri = servletRequest.defaultRedirectUri("/api/auth/oauth2/callback")
    val view = authApplicationService.completeOAuthLogin(code, state, redirectUri, client)
    return sessionCookieWriter.loginResponse(LoginResponse.from(view), view.sessionSecret)
  }

  @PostMapping("/saml2/acs")
  @Operation(
    summary = "SAML assertion consumer",
    description = "Completes SAML login from the identity provider POST.",
  )
  suspend fun samlAcs(
    @RequestParam("SAMLResponse") samlResponse: String,
    @RequestParam("RelayState") relayState: String,
    servletRequest: HttpServletRequest,
  ): ResponseEntity<LoginResponse> {
    val client = HttpClientContext.from(servletRequest).toServiceContext()
    val view = authApplicationService.completeSamlLogin(samlResponse, relayState, client)
    return sessionCookieWriter.loginResponse(LoginResponse.from(view), view.sessionSecret)
  }

  @PostMapping("/magic-link/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
    summary = "Request magic link",
    description = "Sends a magic-link email for passwordless sign-in.",
  )
  suspend fun requestMagicLink(@Valid @RequestBody request: MagicLinkRequest) {
    authApplicationService.requestMagicLink(
      email = request.email,
      tenantId = request.tenantId,
      loginMethodId = request.loginMethodId,
    )
  }

  @GetMapping("/magic-link/verify")
  @Operation(
    summary = "Verify magic link",
    description = "Completes magic-link login from the email link.",
  )
  suspend fun verifyMagicLink(
    @RequestParam token: String,
    servletRequest: HttpServletRequest,
  ): ResponseEntity<LoginResponse> {
    val client = HttpClientContext.from(servletRequest).toServiceContext()
    val view = authApplicationService.verifyMagicLink(token, client)
    return sessionCookieWriter.loginResponse(LoginResponse.from(view), view.sessionSecret)
  }
}
