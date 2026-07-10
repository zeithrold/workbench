package ink.doa.workbench.service.instance

import ink.doa.workbench.agile.workitem.TenantDefaultWorkItemTemplateService
import ink.doa.workbench.security.identity.TenantLoginMethodService
import ink.doa.workbench.security.identity.UserLookupService
import ink.doa.workbench.security.invitation.InvitationService
import ink.doa.workbench.security.permission.AdminUserService
import ink.doa.workbench.tenant.tenant.TenantService
import java.time.Clock
import org.springframework.stereotype.Component

@Component
@Suppress("LongParameterList")
class TenantManagementDependencies(
  val tenants: TenantService,
  val tenantLoginMethods: TenantLoginMethodService,
  val userLookupService: UserLookupService,
  val adminUserService: AdminUserService,
  val invitationService: InvitationService,
  val defaultWorkItemTemplate: TenantDefaultWorkItemTemplateService,
  val clock: Clock,
)
