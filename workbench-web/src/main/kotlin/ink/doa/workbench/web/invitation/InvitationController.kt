package ink.doa.workbench.web.invitation

import ink.doa.workbench.core.identity.model.AcceptInvitationCommand
import ink.doa.workbench.security.invitation.InvitationService
import ink.doa.workbench.web.api.PublicEndpoint
import ink.doa.workbench.web.api.StandardErrorResponses
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/invitations")
@Tag(name = "Invitations", description = "Tenant invitation preview and acceptance.")
@StandardErrorResponses
class InvitationController(private val service: InvitationService) {
  @GetMapping("/preview")
  @PublicEndpoint
  @Operation(
    summary = "Preview an invitation",
    description = "Returns invitation type and tenant summary for the given token.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Invitation preview",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = InvitationPreviewResponse::class),
              )
            ],
        )
      ],
  )
  suspend fun preview(
    @Parameter(description = "Invitation token from the invitation link.")
    @RequestParam
    token: String
  ): InvitationPreviewResponse = InvitationPreviewResponse.from(service.preview(token))

  @PostMapping("/accept")
  @PublicEndpoint
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Accept an invitation",
    description = "Accepts a tenant administrator invitation and creates the invited user account.",
    responses =
      [
        ApiResponse(
          responseCode = "201",
          description = "Invitation accepted",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = InvitationAcceptResponse::class),
              )
            ],
        )
      ],
  )
  suspend fun accept(
    @Valid @RequestBody request: AcceptInvitationRequest
  ): InvitationAcceptResponse =
    InvitationAcceptResponse.from(
      service.accept(
        AcceptInvitationCommand(
          token = request.token,
          displayName = request.displayName,
          password = request.password,
        )
      )
    )
}

@Schema(description = "Accept invitation request.")
data class AcceptInvitationRequest(
  @field:NotBlank val token: String,
  @field:NotBlank @field:Schema(example = "Acme Admin") val displayName: String,
  @field:NotBlank
  @field:Size(min = 12, max = 128)
  @field:Schema(description = "Password for the new user account.")
  val password: String,
)
