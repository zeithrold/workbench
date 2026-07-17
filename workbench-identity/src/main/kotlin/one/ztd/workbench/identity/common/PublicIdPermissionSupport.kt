package one.ztd.workbench.identity.common

import one.ztd.workbench.identity.permission.AccessGrantRepository
import one.ztd.workbench.identity.permission.AdminUserQueryRepository
import org.springframework.stereotype.Component

@Component
class PublicIdPermissionSupport(
  val adminUserQueries: AdminUserQueryRepository,
  val accessGrants: AccessGrantRepository,
)
