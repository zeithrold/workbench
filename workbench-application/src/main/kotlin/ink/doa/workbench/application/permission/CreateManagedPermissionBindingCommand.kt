package ink.doa.workbench.application.permission

import ink.doa.workbench.identity.permission.PermissionPrincipalType
import ink.doa.workbench.identity.permission.model.PermissionEffect
import java.time.OffsetDateTime
import java.util.UUID

data class CreateManagedPermissionBindingCommand(
  val tenantId: UUID,
  val principalType: PermissionPrincipalType,
  val userPublicId: String?,
  val groupPublicId: String?,
  val policyPublicId: String,
  val effect: PermissionEffect?,
  val actorUserId: UUID?,
  val validTo: OffsetDateTime? = null,
)
