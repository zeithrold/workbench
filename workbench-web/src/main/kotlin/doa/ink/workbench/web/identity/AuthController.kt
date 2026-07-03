package doa.ink.workbench.web.identity

import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.service.identity.AuthApplicationService
import doa.ink.workbench.web.api.OpenApiExamples
import doa.ink.workbench.web.api.SessionSecured
import doa.ink.workbench.web.api.StandardErrorResponses
import doa.ink.workbench.web.api.http.HttpClientContext
import doa.ink.workbench.web.api.http.SessionCookieWriter
import doa.ink.workbench.web.api.http.bearerTokenValue
import doa.ink.workbench.web.api.http.defaultRedirectUri
import doa.ink.workbench.web.api.http.sessionCookieValue
import doa.ink.workbench.web.api.http.toServiceContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
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
@Tag(
  name = "Auth",
  description =
    "Authentication and session protocol. Public endpoints establish sessions; secured endpoints require WORKBENCH_SESSION.",
)
@StandardErrorResponses
@Suppress("TooManyFunctions")
class AuthController(
  private val authApplicationService: AuthApplicationService,
  private val sessionCookieWriter: SessionCookieWriter,
) {
  @PostMapping("/login")
  @Operation(
    summary = "Sign in",
    description =
      "Authenticates the user and sets the WORKBENCH_SESSION cookie. Public endpoint with " +
        "no prior session required. Optionally returns a bearer token when issueBearerToken is true.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Login succeeded",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = LoginResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "sessionOnly",
                      summary = "Session cookie only",
                      value = OpenApiExamples.LOGIN_SUCCESS,
                    ),
                    ExampleObject(
                      name = "withBearerToken",
                      summary = "Session plus bearer token",
                      value = OpenApiExamples.LOGIN_SUCCESS_WITH_BEARER,
                    ),
                  ],
              )
            ],
        ),
        ApiResponse(
          responseCode = "401",
          description = "Authentication failed",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "invalidCredentials",
                      value = OpenApiExamples.AUTHENTICATION_FAILED,
                    )
                  ],
              )
            ],
        ),
      ],
  )
  suspend fun login(
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
      description = "Credentials and login method selection.",
      content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = LoginRequest::class),
            examples =
              [
                ExampleObject(
                  name = "passwordLogin",
                  summary = "Password sign-in",
                  value = OpenApiExamples.LOGIN_REQUEST_PASSWORD,
                )
              ],
          )
        ],
    )
    @Valid
    @RequestBody
    request: LoginRequest,
    servletRequest: HttpServletRequest,
  ): ResponseEntity<LoginResponse> {
    val client = HttpClientContext.from(servletRequest).toServiceContext()
    val view = authApplicationService.login(request.toCommand(client))
    return sessionCookieWriter.loginResponse(LoginResponse.from(view), view.sessionSecret)
  }

  @PostMapping("/logout")
  @Operation(
    summary = "Sign out",
    description =
      "Revokes the active session or bearer token and clears the WORKBENCH_SESSION cookie. " +
        "Accepts either the session cookie or Authorization: Bearer header.",
    responses =
      [ApiResponse(responseCode = "200", description = "Logged out; session cookie cleared")],
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
    description =
      "Lists tenant memberships for the authenticated user. Each item embeds the tenant summary.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "User memberships",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = MembershipResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      value = OpenApiExamples.MEMBERSHIP_LIST,
                    )
                  ],
              )
            ],
        )
      ],
  )
  suspend fun memberships(principal: AuthenticatedPrincipal): List<MembershipResponse> =
    authApplicationService.listMemberships(principal).map { MembershipResponse.from(it) }

  @GetMapping("/login-options")
  @Operation(
    summary = "List login options",
    description =
      "Discovers tenant and login method options for an identifier such as an email address. " +
        "Public endpoint used before sign-in to render the login UI.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Available login options",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = LoginOptionResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      value = OpenApiExamples.LOGIN_OPTIONS,
                    )
                  ],
              )
            ],
        )
      ],
  )
  suspend fun loginOptions(
    @Parameter(description = "User identifier such as email.", example = "user@example.com")
    @RequestParam
    identifier: String
  ): List<LoginOptionResponse> =
    authApplicationService.listLoginOptions(identifier).map { LoginOptionResponse.from(it) }

  @GetMapping("/login-discovery")
  @Operation(
    summary = "Discover login flow",
    description =
      "Wizard-oriented login discovery for an identifier. Returns flow type, instance password " +
        "method, or grouped tenant login methods with supported tenants.",
  )
  suspend fun loginDiscovery(
    @Parameter(description = "User identifier such as email.", example = "user@example.com")
    @RequestParam
    identifier: String
  ): LoginDiscoveryResponse =
    LoginDiscoveryResponse.from(authApplicationService.discoverLogin(identifier))

  @PostMapping("/tokens")
  @SessionSecured
  @Operation(
    summary = "Issue bearer token",
    description =
      "Creates a long-lived bearer token for API access. Requires an active session. The token secret is returned only once.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Token issued",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = IssuedTokenResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      value = OpenApiExamples.ISSUED_TOKEN,
                    )
                  ],
              )
            ],
        )
      ],
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
  @Operation(
    summary = "Revoke bearer token",
    description = "Revokes a bearer token by public id. Returns 204 with an empty body.",
    responses =
      [
        ApiResponse(responseCode = "204", description = "Token revoked"),
        ApiResponse(
          responseCode = "404",
          description = "Token not found",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "notFound",
                      value = OpenApiExamples.RESOURCE_NOT_FOUND,
                    )
                  ],
              )
            ],
        ),
      ],
  )
  suspend fun revokeToken(
    @Parameter(description = "Public bearer token id.", example = OpenApiExamples.BEARER_TOKEN_ID)
    @PathVariable
    id: String,
    principal: AuthenticatedPrincipal,
    servletRequest: HttpServletRequest,
  ) {
    val client = HttpClientContext.from(servletRequest).toServiceContext()
    authApplicationService.revokeBearerToken(principal, id, client)
  }

  @PostMapping("/federated/authorize")
  @Operation(
    summary = "Start federated login",
    description =
      "Begins OAuth or SAML authorization for the given tenant and login method. " +
        "Returns a provider authorization URL for the browser to redirect to.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Authorization URL generated",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = FederatedAuthorizeResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      value = OpenApiExamples.FEDERATED_AUTHORIZE,
                    )
                  ],
              )
            ],
        )
      ],
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
    description =
      "Completes OAuth login after the identity provider redirects back. Called by the provider, " +
        "not directly by application clients. Sets the WORKBENCH_SESSION cookie on success.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "OAuth login completed",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = LoginResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      value = OpenApiExamples.LOGIN_SUCCESS,
                    )
                  ],
              )
            ],
        ),
        ApiResponse(
          responseCode = "401",
          description = "OAuth exchange failed",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "authenticationFailed",
                      value = OpenApiExamples.AUTHENTICATION_FAILED,
                    )
                  ],
              )
            ],
        ),
      ],
  )
  suspend fun oauthCallback(
    @Parameter(description = "Authorization code from the provider.") @RequestParam code: String,
    @Parameter(description = "Opaque state echoed from the authorize step.")
    @RequestParam
    state: String,
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
    description =
      "Completes SAML login from the identity provider POST. Called by the IdP ACS endpoint, " +
        "not directly by application clients. Sets the WORKBENCH_SESSION cookie on success.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "SAML login completed",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = LoginResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      value = OpenApiExamples.LOGIN_SUCCESS,
                    )
                  ],
              )
            ],
        ),
        ApiResponse(
          responseCode = "401",
          description = "SAML assertion rejected",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "authenticationFailed",
                      value = OpenApiExamples.AUTHENTICATION_FAILED,
                    )
                  ],
              )
            ],
        ),
      ],
  )
  suspend fun samlAcs(
    @Parameter(description = "Base64-encoded SAML response from the IdP.")
    @RequestParam("SAMLResponse")
    samlResponse: String,
    @Parameter(description = "Relay state from the authorize step.")
    @RequestParam("RelayState")
    relayState: String,
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
    description =
      "Sends a magic-link email for passwordless sign-in. Returns 202 with an empty body. Does not reveal whether the email exists.",
    responses = [ApiResponse(responseCode = "202", description = "Magic link request accepted")],
  )
  suspend fun requestMagicLink(
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = MagicLinkRequest::class),
            examples =
              [
                ExampleObject(
                  name = "valid",
                  value = OpenApiExamples.MAGIC_LINK_REQUEST,
                )
              ],
          )
        ]
    )
    @Valid
    @RequestBody
    request: MagicLinkRequest
  ) {
    authApplicationService.requestMagicLink(
      email = request.email,
      tenantId = request.tenantId,
      loginMethodId = request.loginMethodId,
    )
  }

  @GetMapping("/magic-link/verify")
  @Operation(
    summary = "Verify magic link",
    description =
      "Completes magic-link login from the email link. Sets the WORKBENCH_SESSION cookie on success.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Magic link verified",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = LoginResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      value = OpenApiExamples.LOGIN_SUCCESS,
                    )
                  ],
              )
            ],
        ),
        ApiResponse(
          responseCode = "401",
          description = "Invalid or expired token",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "authenticationFailed",
                      value = OpenApiExamples.AUTHENTICATION_FAILED,
                    )
                  ],
              )
            ],
        ),
      ],
  )
  suspend fun verifyMagicLink(
    @Parameter(description = "Opaque token from the magic-link email.") @RequestParam token: String,
    servletRequest: HttpServletRequest,
  ): ResponseEntity<LoginResponse> {
    val client = HttpClientContext.from(servletRequest).toServiceContext()
    val view = authApplicationService.verifyMagicLink(token, client)
    return sessionCookieWriter.loginResponse(LoginResponse.from(view), view.sessionSecret)
  }
}
