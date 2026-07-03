package doa.ink.workbench.service.instance

import doa.ink.workbench.core.common.context.RequestHost
import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.common.errors.ResourceConflictException
import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.common.errors.WorkbenchErrorCode
import doa.ink.workbench.core.identity.LoginMethodRepository
import doa.ink.workbench.core.identity.TenantLoginMethodSettingRepository
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.model.CreateTenantCommand
import doa.ink.workbench.core.identity.model.CreateTenantLoginMethodSettingCommand
import doa.ink.workbench.core.identity.model.CreateTenantWithAdminCommand
import doa.ink.workbench.core.identity.model.InvitationType
import doa.ink.workbench.core.identity.model.TenantAdminAssignment
import doa.ink.workbench.core.identity.model.TenantRecord
import doa.ink.workbench.core.identity.model.TenantStatus
import doa.ink.workbench.core.identity.model.UpdateTenantCommand
import doa.ink.workbench.core.messaging.EventMetadata
import doa.ink.workbench.core.port.messaging.DomainEventPublisher
import doa.ink.workbench.core.tenant.events.TenantDestroyRequestedEvent
import doa.ink.workbench.core.tenant.events.TenantDomainEvents
import doa.ink.workbench.security.common.PublicIdResolver
import doa.ink.workbench.security.invitation.InvitationService
import doa.ink.workbench.security.permission.AdminUserService
import doa.ink.workbench.security.permission.AdminUserView
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

private const val PASSWORD_METHOD_CODE = "password"

@Service
class TenantManagementService(
  private val tenants: TenantRepository,
  private val users: UserRepository,
  private val loginMethods: LoginMethodRepository,
  private val tenantLoginSettings: TenantLoginMethodSettingRepository,
  private val publicIds: PublicIdResolver,
  private val adminUserService: AdminUserService,
  private val invitationService: InvitationService,
  private val domainEventPublisher: DomainEventPublisher,
  private val clock: Clock,
) {
  suspend fun list(slug: String? = null): List<TenantRecord> = tenants.listForAdmin(slug)

  suspend fun get(tenantPublicId: String): TenantRecord =
    publicIds.resolveTenantForAdmin(tenantPublicId)

  suspend fun create(command: CreateTenantCommand): TenantRecord {
    val tenant = tenants.create(command)
    enablePasswordLoginMethod(tenant.id)
    return tenant
  }

  suspend fun createWithAdmin(
    command: CreateTenantWithAdminCommand,
    actorUserId: UUID?,
    requestHost: RequestHost?,
  ): CreateTenantView {
    val status =
      when (command.adminAssignment) {
        is TenantAdminAssignment.EmailInviteAssignment -> TenantStatus.PENDING_ACTIVATION
        else -> TenantStatus.ACTIVE
      }

    val tenant =
      create(
        CreateTenantCommand(
          name = command.name,
          slug = command.slug,
          timezone = command.timezone,
          locale = command.locale,
          status = status,
        )
      )

    return when (val assignment = command.adminAssignment) {
      is TenantAdminAssignment.SelfAssignment -> {
        val actor =
          actorUserId
            ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
        val adminView =
          adminUserService.provisionTenantAdmin(
            tenantId = tenant.id,
            userId = actor,
            actorUserId = actor,
          )
        CreateTenantView(tenant = tenant, admin = adminView, invitationLink = null)
      }
      is TenantAdminAssignment.UserAssignment -> {
        val adminView =
          adminUserService.provisionTenantAdmin(
            tenantId = tenant.id,
            userId = assignment.userId,
            actorUserId = actorUserId,
          )
        CreateTenantView(tenant = tenant, admin = adminView, invitationLink = null)
      }
      is TenantAdminAssignment.EmailInviteAssignment -> {
        val actor =
          actorUserId
            ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
        val invitation =
          invitationService.create(
            type = InvitationType.TENANT_ADMIN,
            tenantId = tenant.id,
            email = assignment.email,
            displayName = assignment.displayName,
            invitedBy = actor,
            requestHost = requestHost,
          )
        CreateTenantView(
          tenant = tenant,
          admin = null,
          invitationLink = invitation.invitationLink,
        )
      }
    }
  }

  suspend fun update(
    tenantPublicId: String,
    name: String?,
    slug: String?,
    timezone: String?,
    locale: String?,
  ): TenantRecord {
    val tenant = publicIds.resolveTenantForAdmin(tenantPublicId)
    if (tenant.status == TenantStatus.DESTROYING) {
      throw ResourceConflictException(WorkbenchErrorCode.TENANT_DESTROYING_UPDATE_FORBIDDEN)
    }
    return tenants.update(
      UpdateTenantCommand(
        tenantId = tenant.id,
        name = name,
        slug = slug,
        timezone = timezone,
        locale = locale,
      )
    )
  }

  suspend fun requestDestroy(
    tenantPublicId: String,
    actorUserId: UUID,
    deleteReason: String?,
  ): TenantRecord {
    val tenant = publicIds.resolveTenantForAdmin(tenantPublicId)
    val actor =
      users.findById(actorUserId)
        ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
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
      tenants.update(UpdateTenantCommand(tenantId = destroying.id, status = previousStatus))
      throw error
    }
    return destroying
  }

  private suspend fun enablePasswordLoginMethod(tenantId: UUID) {
    val passwordMethod =
      loginMethods.findLoginMethodByCode(PASSWORD_METHOD_CODE)
        ?: throw ResourceNotFoundException(
          WorkbenchErrorCode.RESOURCE_PASSWORD_LOGIN_METHOD_NOT_FOUND
        )
    if (tenantLoginSettings.findTenantSetting(tenantId, passwordMethod.id) == null) {
      tenantLoginSettings.createTenantSetting(
        CreateTenantLoginMethodSettingCommand(
          tenantId = tenantId,
          loginMethodId = passwordMethod.id,
        )
      )
    }
  }
}

data class CreateTenantView(
  val tenant: TenantRecord,
  val admin: AdminUserView?,
  val invitationLink: String?,
)
