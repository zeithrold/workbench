package ink.doa.workbench.application.instance

import ink.doa.workbench.application.invitation.CreateManagedInvitationCommand
import ink.doa.workbench.application.permission.AdminUserView
import ink.doa.workbench.identity.model.CreateTenantWithAdminCommand
import ink.doa.workbench.identity.model.InvitationType
import ink.doa.workbench.identity.model.TenantAdminAssignment
import ink.doa.workbench.kernel.common.context.RequestHost
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.tenant.model.CreateTenantCommand
import ink.doa.workbench.tenant.model.TenantRecord
import ink.doa.workbench.tenant.model.TenantStatus
import ink.doa.workbench.tenant.tenant.events.TenantDestroyRequestedEvent
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class TenantManagementApplicationService(private val dependencies: TenantManagementDependencies) {
  private val tenants = dependencies.tenants
  private val tenantLoginMethods = dependencies.identity.tenantLoginMethods
  private val userLookupService = dependencies.identity.userLookupService
  private val adminUserService = dependencies.identity.adminUserService
  private val invitationService = dependencies.identity.invitationService
  private val clock = dependencies.clock
  private val defaultWorkItemTemplate = dependencies.defaultWorkItemTemplate

  suspend fun list(slug: String? = null): List<TenantRecord> = tenants.listForAdmin(slug)

  suspend fun get(tenantPublicId: String): TenantRecord = tenants.getForAdmin(tenantPublicId)

  suspend fun create(command: CreateTenantCommand): TenantRecord {
    val tenant = tenants.create(command)
    tenantLoginMethods.enablePasswordLoginMethod(tenant.id)
    defaultWorkItemTemplate.ensureProvisioned(tenant.id, null)
    return tenant
  }

  suspend fun createWithAdmin(
    command: CreateTenantWithAdminCommand,
    actorUserId: UUID?,
    requestHost: RequestHost?,
  ): CreateTenantView {
    val tenant = createTenantForAdminAssignment(command)
    return when (val assignment = command.adminAssignment) {
      is TenantAdminAssignment.SelfAssignment -> createTenantWithSelfAdmin(tenant, actorUserId)
      is TenantAdminAssignment.UserAssignment ->
        createTenantWithAssignedAdmin(tenant, assignment, actorUserId)
      is TenantAdminAssignment.EmailInviteAssignment ->
        createTenantWithEmailInviteAdmin(tenant, assignment, actorUserId, requestHost)
    }
  }

  private suspend fun createTenantForAdminAssignment(
    command: CreateTenantWithAdminCommand
  ): TenantRecord {
    val status =
      when (command.adminAssignment) {
        is TenantAdminAssignment.EmailInviteAssignment -> TenantStatus.PENDING_ACTIVATION
        else -> TenantStatus.ACTIVE
      }
    return create(
      CreateTenantCommand(
        name = command.name,
        slug = command.slug,
        timezone = command.timezone,
        locale = command.locale,
        status = status,
      )
    )
  }

  private suspend fun createTenantWithSelfAdmin(
    tenant: TenantRecord,
    actorUserId: UUID?,
  ): CreateTenantView {
    val actor =
      actorUserId
        ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
    val adminView =
      adminUserService.provisionTenantAdmin(
        tenantId = tenant.id,
        userId = actor,
        actorUserId = actor,
      )
    return CreateTenantView(tenant = tenant, admin = adminView, invitationLink = null)
  }

  private suspend fun createTenantWithAssignedAdmin(
    tenant: TenantRecord,
    assignment: TenantAdminAssignment.UserAssignment,
    actorUserId: UUID?,
  ): CreateTenantView {
    val adminView =
      adminUserService.provisionTenantAdmin(
        tenantId = tenant.id,
        userId = assignment.userId,
        actorUserId = actorUserId,
      )
    return CreateTenantView(tenant = tenant, admin = adminView, invitationLink = null)
  }

  private suspend fun createTenantWithEmailInviteAdmin(
    tenant: TenantRecord,
    assignment: TenantAdminAssignment.EmailInviteAssignment,
    actorUserId: UUID?,
    requestHost: RequestHost?,
  ): CreateTenantView {
    val actor =
      actorUserId
        ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
    val invitation =
      invitationService.create(
        CreateManagedInvitationCommand(
          type = InvitationType.TENANT_ADMIN,
          tenantId = tenant.id,
          email = assignment.email,
          displayName = assignment.displayName,
          invitedBy = actor,
          requestHost = requestHost,
        )
      )
    return CreateTenantView(
      tenant = tenant,
      admin = null,
      invitationLink = invitation.invitationLink,
    )
  }

  suspend fun update(
    tenantPublicId: String,
    name: String?,
    slug: String?,
    timezone: String?,
    locale: String?,
  ): TenantRecord =
    tenants.update(
      tenantPublicId = tenantPublicId,
      name = name,
      slug = slug,
      timezone = timezone,
      locale = locale,
    )

  suspend fun requestDestroy(
    tenantPublicId: String,
    actorUserId: UUID,
    deleteReason: String?,
  ): TenantRecord {
    val tenant = tenants.getForAdmin(tenantPublicId)
    val actor = userLookupService.requireAuthenticatedUser(actorUserId)
    val now = OffsetDateTime.now(clock)
    return tenants.requestDestroy(
      tenantId = tenant.id,
      tenantApiId = tenant.apiId.value,
      payload =
        TenantDestroyRequestedEvent.from(
          tenant = tenant,
          deleteReason = deleteReason,
          requestedAt = now,
          requestedByPublicId = actor.apiId,
        ),
    )
  }
}

data class CreateTenantView(
  val tenant: TenantRecord,
  val admin: AdminUserView?,
  val invitationLink: String?,
)
