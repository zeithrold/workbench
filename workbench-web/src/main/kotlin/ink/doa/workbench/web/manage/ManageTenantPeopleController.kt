package ink.doa.workbench.web.manage

import ink.doa.workbench.application.identity.TenantMemberManagementService
import ink.doa.workbench.application.identity.TenantMemberView
import ink.doa.workbench.application.invitation.CreateManagedInvitationCommand
import ink.doa.workbench.application.invitation.InvitationService
import ink.doa.workbench.application.invitation.ManagedInvitationService
import ink.doa.workbench.application.invitation.ManagedInvitationView
import ink.doa.workbench.identity.common.summary.UserSummary
import ink.doa.workbench.identity.model.InvitationType
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import ink.doa.workbench.web.api.context.TenantRequestContext
import ink.doa.workbench.web.api.http.HttpClientContext
import ink.doa.workbench.web.invitation.CreateTenantMemberInvitationRequest
import ink.doa.workbench.web.invitation.InvitationCreatedResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import java.time.OffsetDateTime
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class TenantMemberResponse(
  val id: String,
  val user: UserSummary,
  val status: String,
  val administrator: Boolean,
  val joinedAt: OffsetDateTime?,
) {
  companion object {
    fun from(view: TenantMemberView) =
      TenantMemberResponse(
        id = view.id,
        user = view.user,
        status = view.status.name,
        administrator = view.administrator,
        joinedAt = view.joinedAt,
      )
  }
}

data class ManagedInvitationResponse(
  val id: String,
  val type: String,
  val email: String,
  val displayName: String?,
  val expiresAt: OffsetDateTime,
  val createdAt: OffsetDateTime?,
) {
  companion object {
    fun from(view: ManagedInvitationView) =
      ManagedInvitationResponse(
        id = view.id,
        type = view.type.name,
        email = view.email,
        displayName = view.displayName,
        expiresAt = view.expiresAt,
        createdAt = view.createdAt,
      )
  }
}

@RestController
@RequestMapping("/api/manage")
@Authenticated
@TenantScoped
@SessionSecured
@StandardErrorResponses
@Tag(name = "Tenant People Management", description = "Tenant member and invitation lifecycle.")
class ManageTenantPeopleController(
  private val members: TenantMemberManagementService,
  private val invitations: InvitationService,
  private val managedInvitations: ManagedInvitationService,
) {
  @GetMapping("/members")
  @Authorize(action = "tenant.read", resource = "tenant")
  @Operation(
    summary = "List current tenant members",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Tenant members",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun listMembers(tenantContext: TenantRequestContext): List<TenantMemberResponse> =
    members.list(tenantContext.tenant.id).map(TenantMemberResponse::from)

  @PostMapping("/members/{id}/suspend")
  @Authorize(action = "tenant.member.manage", resource = "tenant")
  @Operation(
    summary = "Suspend a tenant member",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Suspended member",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun suspendMember(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ): TenantMemberResponse =
    TenantMemberResponse.from(members.suspendMember(tenantContext.tenant.id, id))

  @PostMapping("/members/{id}/restore")
  @Authorize(action = "tenant.member.manage", resource = "tenant")
  @Operation(
    summary = "Restore a tenant member",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Restored member",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun restoreMember(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ): TenantMemberResponse =
    TenantMemberResponse.from(members.restoreMember(tenantContext.tenant.id, id))

  @DeleteMapping("/members/{id}")
  @Authorize(action = "tenant.member.manage", resource = "tenant")
  @Operation(
    summary = "Remove a tenant member",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Removed member",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun removeMember(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ): TenantMemberResponse =
    TenantMemberResponse.from(members.removeMember(tenantContext.tenant.id, id))

  @GetMapping("/invitations")
  @Authorize(action = "tenant.read", resource = "tenant")
  @Operation(
    summary = "List pending tenant invitations",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Pending invitations",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun listInvitations(
    tenantContext: TenantRequestContext
  ): List<ManagedInvitationResponse> =
    managedInvitations.listPending(tenantContext.tenant.id).map(ManagedInvitationResponse::from)

  @PostMapping("/invitations")
  @Authorize(action = "tenant.member.manage", resource = "tenant")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Invite a tenant member",
    responses =
      [
        ApiResponse(
          responseCode = "201",
          description = "Invitation created",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun invite(
    @Valid @RequestBody request: CreateTenantMemberInvitationRequest,
    tenantContext: TenantRequestContext,
    httpRequest: HttpServletRequest,
  ): InvitationCreatedResponse =
    InvitationCreatedResponse.from(
      invitations.create(
        CreateManagedInvitationCommand(
          type = InvitationType.TENANT_MEMBER,
          tenantId = tenantContext.tenant.id,
          email = request.email,
          displayName = request.displayName,
          invitedBy =
            tenantContext.actor?.id
              ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED),
          requestHost = HttpClientContext.resolveRequestHost(httpRequest),
        )
      )
    )

  @DeleteMapping("/invitations/{id}")
  @Authorize(action = "tenant.member.manage", resource = "tenant")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Cancel a pending tenant invitation",
    responses = [ApiResponse(responseCode = "204", description = "Invitation cancelled")],
  )
  suspend fun cancelInvitation(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ) {
    managedInvitations.cancel(tenantContext.tenant.id, id)
  }
}
