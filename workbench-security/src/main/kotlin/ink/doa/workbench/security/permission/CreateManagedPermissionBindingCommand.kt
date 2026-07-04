package ink.doa.workbench.security.permission

import ink.doa.workbench.core.permission.PermissionPrincipalType
import ink.doa.workbench.core.permission.model.PermissionEffect
import java.util.UUID

data class CreateManagedPermissionBindingCommand(
  val tenantId: UUID,
  val principalType: PermissionPrincipalType,
  val userPublicId: String?,
  val groupPublicId: String?,
  val policyPublicId: String,
  val projectPublicId: String?,
  val effect: PermissionEffect?,
  val actorUserId: UUID?,
)
