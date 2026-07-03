package doa.ink.workbench.web.identity

import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.security.identity.SessionService
import doa.ink.workbench.web.api.OpenApiExamples
import doa.ink.workbench.web.api.SessionSecured
import doa.ink.workbench.web.api.StandardErrorResponses
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/session")
@Tag(
  name = "Session",
  description =
    "Current session aggregate keyed by the WORKBENCH_SESSION cookie. Not a persistent CRUD resource.",
)
@SessionSecured
@StandardErrorResponses
class SessionController(private val sessionService: SessionService) {
  @GetMapping
  @Operation(
    summary = "Get session",
    description =
      "Returns the authenticated user and active tenant for the current session. Use this to hydrate client state after page load.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Current session",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = SessionResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      summary = "Session with active tenant",
                      value = OpenApiExamples.SESSION_ACTIVE,
                    )
                  ],
              )
            ],
        )
      ],
  )
  suspend fun getSession(principal: AuthenticatedPrincipal): SessionResponse =
    SessionResponse.from(sessionService.getCurrent(principal))

  @PatchMapping
  @Operation(
    summary = "Switch tenant",
    description =
      "Sets the active tenant for the current session. The user must be a member of the requested tenant.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Tenant switched",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = SessionResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      value = OpenApiExamples.SESSION_ACTIVE,
                    )
                  ],
              )
            ],
        ),
        ApiResponse(
          responseCode = "409",
          description = "Tenant cannot be selected",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "notMember",
                      summary = "User is not a member of the tenant",
                      value = OpenApiExamples.TENANT_NOT_SELECTED,
                    )
                  ],
              )
            ],
        ),
      ],
  )
  suspend fun switchTenant(
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = SwitchTenantRequest::class),
            examples =
              [
                ExampleObject(
                  name = "valid",
                  value = OpenApiExamples.SWITCH_TENANT_REQUEST,
                )
              ],
          )
        ]
    )
    @Valid
    @RequestBody
    request: SwitchTenantRequest,
    principal: AuthenticatedPrincipal,
  ): SessionResponse =
    SessionResponse.from(sessionService.switchTenant(principal, request.tenantId))
}
