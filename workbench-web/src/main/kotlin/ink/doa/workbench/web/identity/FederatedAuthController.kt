package ink.doa.workbench.web.identity

import ink.doa.workbench.identity.FederatedLoginCompletionService
import ink.doa.workbench.identity.auth.FederatedAuthService
import ink.doa.workbench.web.api.OpenApiExamples
import ink.doa.workbench.web.api.PublicEndpoint
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.http.HttpClientContext
import ink.doa.workbench.web.api.http.SessionCookieWriter
import ink.doa.workbench.web.api.http.defaultRedirectUri
import ink.doa.workbench.web.api.http.toServiceContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
@Tag(
  name = "Auth",
  description =
    "Authentication and session protocol. Public endpoints establish sessions; secured endpoints require WORKBENCH_SESSION.",
)
@StandardErrorResponses
class FederatedAuthController(
  private val federatedAuthService: FederatedAuthService,
  private val federatedLoginCompletionService: FederatedLoginCompletionService,
  private val sessionCookieWriter: SessionCookieWriter,
) {
  @PostMapping("/federated/authorize")
  @PublicEndpoint
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
    val result =
      federatedAuthService.beginAuthorize(
        loginMethodId = request.loginMethodId,
        tenantId = request.tenantId,
        returnUrl = request.returnUrl,
        redirectUri = redirectUri,
      )
    return FederatedAuthorizeResponse.from(
      ink.doa.workbench.identity.FederatedAuthorizeView(
        authorizationUrl = result.authorizationUrl,
        state = result.state,
      )
    )
  }

  @GetMapping("/oauth2/callback")
  @PublicEndpoint
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
    val federated = federatedAuthService.completeOAuthCallback(code, state, redirectUri)
    val view = federatedLoginCompletionService.complete(federated, client)
    return sessionCookieWriter.loginResponse(LoginResponse.from(view), view.sessionSecret)
  }

  @PostMapping("/saml2/acs")
  @PublicEndpoint
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
    val federated = federatedAuthService.completeSamlAcs(samlResponse, relayState)
    val view = federatedLoginCompletionService.complete(federated, client)
    return sessionCookieWriter.loginResponse(LoginResponse.from(view), view.sessionSecret)
  }
}
