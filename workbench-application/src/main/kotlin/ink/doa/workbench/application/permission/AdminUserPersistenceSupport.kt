package ink.doa.workbench.application.permission

import ink.doa.workbench.identity.TenantMemberRepository
import ink.doa.workbench.identity.UserRepository
import ink.doa.workbench.identity.permission.AccessGrantRepository
import ink.doa.workbench.identity.permission.AdminUserCommandRepository
import ink.doa.workbench.identity.permission.AdminUserQueryRepository
import org.springframework.stereotype.Component

@Component
class AdminUserPersistenceSupport(
  val adminUserCommands: AdminUserCommandRepository,
  val adminUserQueries: AdminUserQueryRepository,
  val accessGrants: AccessGrantRepository,
  val userRepository: UserRepository,
  val tenantMembers: TenantMemberRepository,
)
