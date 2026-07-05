package ink.doa.workbench.security.permission

import ink.doa.workbench.core.permission.GrantScope
import ink.doa.workbench.core.permission.model.PermissionEffect
import java.util.UUID

data class CreateManagedAccessGrantCommand(
  val scope: GrantScope,
  val tenantId: UUID?,
  val userPublicId: String,
  val actionCode: String,
  val resourcePattern: String,
  val effect: PermissionEffect,
  val projectPublicId: String?,
  val actorUserId: UUID?,
)
