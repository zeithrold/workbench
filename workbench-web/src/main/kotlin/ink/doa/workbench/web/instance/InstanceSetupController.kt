package ink.doa.workbench.web.instance

import ink.doa.workbench.service.instance.InstanceSetupApplicationService
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.http.HttpClientContext
import ink.doa.workbench.web.api.http.SessionCookieWriter
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
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
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/instance")
@Tag(
  name = "Instance Setup",
  description = "First-run bootstrap for creating the initial system administrator.",
)
@StandardErrorResponses
class InstanceSetupController(
  private val service: InstanceSetupApplicationService,
  private val sessionCookieWriter: SessionCookieWriter,
) {
  @GetMapping("/setup-status")
  @Operation(
    summary = "Get instance initialization status",
    description = "Returns whether a system administrator already exists.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Initialization status",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = InstanceSetupStatusResponse::class),
              )
            ],
        )
      ],
  )
  suspend fun setupStatus(): InstanceSetupStatusResponse {
    val view = service.setupStatus()
    return InstanceSetupStatusResponse(initialized = view.initialized)
  }

  @PostMapping("/setup")
  @Operation(
    summary = "Bootstrap instance administrator",
    description =
      "Creates the first system administrator, password login account, and an authenticated " +
        "session. Available only while no system user exists.",
    responses =
      [
        ApiResponse(
          responseCode = "201",
          description = "Instance administrator created",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = InstanceBootstrapResponse::class),
              )
            ],
        ),
        ApiResponse(
          responseCode = "403",
          description = "Setup token invalid",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
              )
            ],
        ),
        ApiResponse(
          responseCode = "409",
          description = "Instance already initialized",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
              )
            ],
        ),
      ],
  )
  suspend fun setup(
    @Valid @RequestBody request: InstanceSetupRequest,
    httpRequest: HttpServletRequest,
  ): ResponseEntity<InstanceBootstrapResponse> {
    val client = HttpClientContext.from(httpRequest)
    val view = service.bootstrap(request.toCommand(client.ipAddress, client.userAgent))
    val response = InstanceBootstrapResponse.from(view)
    return sessionCookieWriter.createdWithSession(
      body = response,
      sessionSecret = view.session.sessionSecret,
      sessionExpiresAt = view.session.sessionExpiresAt,
    )
  }
}
