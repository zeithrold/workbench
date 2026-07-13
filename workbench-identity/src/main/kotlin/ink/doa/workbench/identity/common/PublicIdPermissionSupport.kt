package ink.doa.workbench.identity.common

import ink.doa.workbench.identity.permission.AccessGrantRepository
import ink.doa.workbench.identity.permission.AdminUserQueryRepository
import org.springframework.stereotype.Component

@Component
class PublicIdPermissionSupport(
  val adminUserQueries: AdminUserQueryRepository,
  val accessGrants: AccessGrantRepository,
)
