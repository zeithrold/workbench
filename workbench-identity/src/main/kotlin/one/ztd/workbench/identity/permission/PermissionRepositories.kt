package one.ztd.workbench.identity.permission

import java.time.OffsetDateTime
import one.ztd.workbench.identity.permission.model.AuthorizationAction

data class PermissionActionRecord(
  val id: java.util.UUID,
  val code: AuthorizationAction,
  val description: String?,
  val createdAt: OffsetDateTime,
)

data class CreatePermissionActionCommand(
  val code: AuthorizationAction,
  val description: String? = null,
)

interface PermissionActionRepository {
  suspend fun upsert(command: CreatePermissionActionCommand): PermissionActionRecord

  suspend fun findByCode(code: AuthorizationAction): PermissionActionRecord?

  suspend fun list(): List<PermissionActionRecord>
}
