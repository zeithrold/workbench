package one.ztd.workbench.application.instance

import java.time.Clock
import one.ztd.workbench.agile.workitem.TenantDefaultWorkItemTemplateService
import one.ztd.workbench.application.invitation.InvitationService
import one.ztd.workbench.application.permission.AdminUserService
import one.ztd.workbench.identity.TenantLoginMethodService
import one.ztd.workbench.identity.UserLookupService
import one.ztd.workbench.tenant.tenant.TenantService
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
