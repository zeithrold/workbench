package ink.doa.workbench.service.instance

import ink.doa.workbench.core.common.context.RequestHost
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.identity.model.CreateTenantCommand
import ink.doa.workbench.core.identity.model.CreateTenantWithAdminCommand
import ink.doa.workbench.core.identity.model.InvitationType
import ink.doa.workbench.core.identity.model.TenantAdminAssignment
import ink.doa.workbench.core.identity.model.TenantRecord
import ink.doa.workbench.core.identity.model.TenantStatus
import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import ink.doa.workbench.core.tenant.events.TenantDestroyRequestedEvent
import ink.doa.workbench.core.tenant.events.TenantDomainEvents
import ink.doa.workbench.security.invitation.CreateManagedInvitationCommand
import ink.doa.workbench.security.permission.AdminUserView
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class TenantManagementApplicationService(
  private val dependencies: TenantManagementDependencies,
  private val domainEventPublisher: DomainEventPublisher,
) {
  private val tenants = dependencies.tenants
  private val tenantLoginMethods = dependencies.tenantLoginMethods
  private val userLookupService = dependencies.userLookupService
  private val adminUserService = dependencies.adminUserService
  private val invitationService = dependencies.invitationService
  private val clock = dependencies.clock

  suspend fun list(slug: String? = null): List<TenantRecord> = tenants.listForAdmin(slug)

  suspend fun get(tenantPublicId: String): TenantRecord = tenants.getForAdmin(tenantPublicId)

  suspend fun create(command: CreateTenantCommand): TenantRecord {
    val tenant = tenants.create(command)
    tenantLoginMethods.enablePasswordLoginMethod(tenant.id)
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
    val previousStatus = tenant.status
    val now = OffsetDateTime.now(clock)
    val destroying = tenants.markDestroying(tenant.id)
    try {
      domainEventPublisher.publish(
        spec = TenantDomainEvents.DestroyRequested,
        key = destroying.apiId.value,
        payload =
          TenantDestroyRequestedEvent.from(
            tenant = destroying,
            deleteReason = deleteReason,
            requestedAt = now,
            requestedByPublicId = actor.apiId,
          ),
        metadata = EventMetadata(tenantId = destroying.apiId.value),
      )
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
      tenants.restoreStatus(destroying.id, previousStatus)
      throw error
    }
    return destroying
  }
}

data class CreateTenantView(
  val tenant: TenantRecord,
  val admin: AdminUserView?,
  val invitationLink: String?,
)
