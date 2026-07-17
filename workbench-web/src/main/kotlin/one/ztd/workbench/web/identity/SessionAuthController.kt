package one.ztd.workbench.web.identity

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import one.ztd.workbench.identity.AuthApplicationService
import one.ztd.workbench.identity.MembershipService
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.web.api.AuthenticatedOnly
import one.ztd.workbench.web.api.OpenApiExamples
import one.ztd.workbench.web.api.PublicEndpoint
import one.ztd.workbench.web.api.SessionSecured
import one.ztd.workbench.web.api.StandardErrorResponses
import one.ztd.workbench.web.api.http.HttpClientContext
import one.ztd.workbench.web.api.http.SessionCookieWriter
import one.ztd.workbench.web.api.http.bearerTokenValue
import one.ztd.workbench.web.api.http.sessionCookieValue
import one.ztd.workbench.web.api.http.toServiceContext
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
@Tag(
  name = "Auth",
  description =
    "Authentication and session protocol. Public endpoints establish sessions; secured endpoints require WORKBENCH_SESSION.",
)
@StandardErrorResponses
class SessionAuthController(
  private val authApplicationService: AuthApplicationService,
  private val membershipService: MembershipService,
  private val sessionCookieWriter: SessionCookieWriter,
) {
  @PostMapping("/login")
  @PublicEndpoint
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
  @AuthenticatedOnly
  @Operation(
    summary = "Sign out",
    description =
      "Revokes the active session or bearer token and clears the WORKBENCH_SESSION cookie. " +
        "Accepts either the session cookie or Authorization: Bearer header.",
    responses =
      [ApiResponse(responseCode = "200", description = "Logged out; session cookie cleared")],
  )
  suspend fun logout(servletRequest: HttpServletRequest): ResponseEntity<Unit> {
    val client = HttpClientContext.from(servletRequest).toServiceContext()
    authApplicationService.logout(
      client = client,
      sessionSecret = servletRequest.sessionCookieValue(),
      bearerToken = servletRequest.bearerTokenValue(),
    )
    return sessionCookieWriter.logoutResponse()
  }

  @GetMapping("/memberships")
  @AuthenticatedOnly
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
    membershipService.listActiveMemberships(principal.user.id).map { MembershipResponse.from(it) }
}
