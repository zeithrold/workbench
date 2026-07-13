package ink.doa.workbench.application.permission

import ink.doa.workbench.identity.permission.GrantScope
import ink.doa.workbench.identity.permission.model.PermissionEffect
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
