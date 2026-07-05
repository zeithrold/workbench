package ink.doa.workbench.security.common

import ink.doa.workbench.core.permission.AccessGrantRepository
import ink.doa.workbench.core.permission.AdminUserQueryRepository
import org.springframework.stereotype.Component

@Component
class PublicIdPermissionSupport(
  val adminUserQueries: AdminUserQueryRepository,
  val accessGrants: AccessGrantRepository,
)
