package one.ztd.workbench.application.permission

import one.ztd.workbench.identity.TenantMemberRepository
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.identity.permission.AccessGrantRepository
import one.ztd.workbench.identity.permission.AdminUserCommandRepository
import one.ztd.workbench.identity.permission.AdminUserQueryRepository
import org.springframework.stereotype.Component

@Component
class AdminUserPersistenceSupport(
  val adminUserCommands: AdminUserCommandRepository,
  val adminUserQueries: AdminUserQueryRepository,
  val accessGrants: AccessGrantRepository,
  val userRepository: UserRepository,
  val tenantMembers: TenantMemberRepository,
)
