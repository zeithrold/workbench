package ink.doa.workbench.security.permission

import ink.doa.workbench.core.identity.TenantMemberRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.permission.AccessGrantRepository
import ink.doa.workbench.core.permission.AdminUserCommandRepository
import ink.doa.workbench.core.permission.AdminUserQueryRepository
import org.springframework.stereotype.Component

@Component
class AdminUserPersistenceSupport(
  val adminUserCommands: AdminUserCommandRepository,
  val adminUserQueries: AdminUserQueryRepository,
  val accessGrants: AccessGrantRepository,
  val userRepository: UserRepository,
  val tenantMembers: TenantMemberRepository,
)
