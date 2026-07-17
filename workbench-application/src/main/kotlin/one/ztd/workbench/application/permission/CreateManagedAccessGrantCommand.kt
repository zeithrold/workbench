package one.ztd.workbench.application.permission

import java.util.UUID
import one.ztd.workbench.identity.permission.GrantScope
import one.ztd.workbench.identity.permission.model.PermissionEffect

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
