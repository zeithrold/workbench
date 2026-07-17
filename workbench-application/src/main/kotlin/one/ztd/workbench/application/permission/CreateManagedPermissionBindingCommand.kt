package one.ztd.workbench.application.permission

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.identity.permission.PermissionPrincipalType
import one.ztd.workbench.identity.permission.model.PermissionEffect

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
