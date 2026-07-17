package one.ztd.workbench.web.invitation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import one.ztd.workbench.application.invitation.CreateManagedInvitationCommand
import one.ztd.workbench.application.invitation.InvitationService
import one.ztd.workbench.identity.model.AcceptInvitationCommand
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.web.api.Authenticated
import one.ztd.workbench.web.api.AuthenticatedOnly
import one.ztd.workbench.web.api.Authorize
import one.ztd.workbench.web.api.PublicEndpoint
import one.ztd.workbench.web.api.SessionSecured
import one.ztd.workbench.web.api.StandardErrorResponses
import one.ztd.workbench.web.api.TenantScoped
import one.ztd.workbench.web.api.context.TenantRequestContext
import one.ztd.workbench.web.api.http.HttpClientContext
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
  @PostMapping
  @SessionSecured
  @Authenticated
  @TenantScoped
  @Authorize(action = "tenant.member.manage", resource = "tenant")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a tenant member invitation")
  suspend fun create(
    @Valid @RequestBody request: CreateTenantMemberInvitationRequest,
    tenantContext: TenantRequestContext,
    httpRequest: HttpServletRequest,
  ): InvitationCreatedResponse =
    InvitationCreatedResponse.from(
      service.create(
        CreateManagedInvitationCommand(
          type = one.ztd.workbench.identity.model.InvitationType.TENANT_MEMBER,
          tenantId = tenantContext.tenant.id,
          email = request.email,
          displayName = request.displayName,
          invitedBy = actorUserId(tenantContext),
          requestHost = HttpClientContext.resolveRequestHost(httpRequest),
        )
      )
    )

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

  @PostMapping("/accept-existing")
  @SessionSecured
  @Authenticated
  @AuthenticatedOnly
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Accept a tenant invitation as an existing user")
  suspend fun acceptExisting(
    @Valid @RequestBody request: AcceptExistingInvitationRequest,
    principal: AuthenticatedPrincipal,
  ): InvitationAcceptResponse =
    InvitationAcceptResponse.from(service.acceptExisting(request.token, principal.user))
}

data class CreateTenantMemberInvitationRequest(
  @field:NotBlank val email: String,
  val displayName: String? = null,
)

data class AcceptExistingInvitationRequest(@field:NotBlank val token: String)

data class InvitationCreatedResponse(
  val id: String,
  val email: String,
  val expiresAt: java.time.OffsetDateTime,
  val invitationLink: String,
) {
  companion object {
    fun from(result: one.ztd.workbench.application.invitation.CreateInvitationResult) =
      InvitationCreatedResponse(result.id, result.email, result.expiresAt, result.invitationLink)
  }
}

private fun actorUserId(tenantContext: TenantRequestContext) =
  tenantContext.actor?.id
    ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)

@Schema(description = "Accept invitation request.")
data class AcceptInvitationRequest(
  @field:NotBlank val token: String,
  @field:NotBlank @field:Schema(example = "Acme Admin") val displayName: String,
  @field:NotBlank
  @field:Size(min = 12, max = 128)
  @field:Schema(description = "Password for the new user account.")
  val password: String,
)
