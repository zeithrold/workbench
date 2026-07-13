package ink.doa.workbench.application.instance

import ink.doa.workbench.agile.workitem.TenantDefaultWorkItemTemplateService
import ink.doa.workbench.application.invitation.InvitationService
import ink.doa.workbench.application.permission.AdminUserService
import ink.doa.workbench.identity.TenantLoginMethodService
import ink.doa.workbench.identity.UserLookupService
import ink.doa.workbench.tenant.tenant.TenantService
import java.time.Clock
import org.springframework.stereotype.Component

@Component
class TenantIdentityDependencies(
  val tenantLoginMethods: TenantLoginMethodService,
  val userLookupService: UserLookupService,
  val adminUserService: AdminUserService,
  val invitationService: InvitationService,
)

@Component
class TenantManagementDependencies(
  val tenants: TenantService,
  val identity: TenantIdentityDependencies,
  val defaultWorkItemTemplate: TenantDefaultWorkItemTemplateService,
  val clock: Clock,
)
