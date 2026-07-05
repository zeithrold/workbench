package ink.doa.workbench.web.identity

import ink.doa.workbench.security.identity.FederatedLoginCompletionService
import ink.doa.workbench.security.identity.auth.MagicLinkAuthService
import ink.doa.workbench.web.api.OpenApiExamples
import ink.doa.workbench.web.api.PublicEndpoint
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.http.HttpClientContext
import ink.doa.workbench.web.api.http.SessionCookieWriter
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
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
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
class MagicLinkAuthController(
  private val magicLinkAuthService: MagicLinkAuthService,
  private val federatedLoginCompletionService: FederatedLoginCompletionService,
  private val sessionCookieWriter: SessionCookieWriter,
) {
  @PostMapping("/magic-link/request")
  @PublicEndpoint
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
    magicLinkAuthService.requestMagicLink(
      email = request.email,
      tenantId = request.tenantId,
      loginMethodId = request.loginMethodId,
    )
  }

  @GetMapping("/magic-link/verify")
  @PublicEndpoint
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
    val identity = magicLinkAuthService.resolveToken(token)
    val view =
      federatedLoginCompletionService.complete(
        ink.doa.workbench.security.identity.auth.FederatedLoginResult(
          identity =
            ink.doa.workbench.core.identity.model.AuthenticatedIdentity(
              user = identity.user,
              loginAccount = identity.loginAccount,
            ),
          tenantId = identity.tenantId,
        ),
        client,
      )
    return sessionCookieWriter.loginResponse(LoginResponse.from(view), view.sessionSecret)
  }
}
