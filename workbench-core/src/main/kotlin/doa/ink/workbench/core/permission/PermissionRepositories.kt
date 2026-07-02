package doa.ink.workbench.core.permission

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.permission.model.AuthorizationAction
import doa.ink.workbench.core.permission.model.PermissionCondition
import doa.ink.workbench.core.permission.model.PermissionEffect
import java.time.OffsetDateTime
import java.util.UUID

enum class RoleScope(val dbValue: String) {
  SYSTEM("system"),
  TENANT("tenant"),
  PROJECT("project"),
}

data class RoleRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID?,
  val scope: RoleScope,
  val code: String,
  val name: String,
  val description: String?,
  val isBuiltin: Boolean,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class PermissionActionRecord(
  val id: UUID,
  val code: AuthorizationAction,
  val description: String?,
  val createdAt: OffsetDateTime,
)

data class PermissionPolicyRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val roleId: UUID,
  val action: AuthorizationAction,
  val effect: PermissionEffect,
  val resourcePattern: String,
  val condition: PermissionCondition?,
  val version: Int,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime?,
  val createdBy: UUID?,
  val createdAt: OffsetDateTime,
)

data class RoleAssignmentRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val userId: UUID,
  val roleId: UUID,
  val projectId: UUID?,
  val grantedBy: UUID?,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime?,
  val createdAt: OffsetDateTime,
)

data class CreateRoleCommand(
  val tenantId: UUID?,
  val scope: RoleScope,
  val code: String,
  val name: String,
  val description: String? = null,
  val isBuiltin: Boolean = false,
)

data class CreatePermissionActionCommand(
  val code: AuthorizationAction,
  val description: String? = null,
)

data class CreatePermissionPolicyCommand(
  val tenantId: UUID,
  val roleId: UUID,
  val action: AuthorizationAction,
  val effect: PermissionEffect = PermissionEffect.ALLOW,
  val resourcePattern: String,
  val condition: PermissionCondition? = null,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime? = null,
  val createdBy: UUID? = null,
)

data class AssignRoleCommand(
  val tenantId: UUID,
  val userId: UUID,
  val roleId: UUID,
  val projectId: UUID? = null,
  val grantedBy: UUID? = null,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime? = null,
)

interface RoleRepository {
  suspend fun create(command: CreateRoleCommand): RoleRecord

  suspend fun findById(id: UUID): RoleRecord?

  suspend fun findByCode(tenantId: UUID?, code: String): RoleRecord?

  suspend fun list(tenantId: UUID?): List<RoleRecord>
}

interface PermissionActionRepository {
  suspend fun upsert(command: CreatePermissionActionCommand): PermissionActionRecord

  suspend fun findByCode(code: AuthorizationAction): PermissionActionRecord?

  suspend fun list(): List<PermissionActionRecord>
}

interface PermissionPolicyRepository {
  suspend fun create(command: CreatePermissionPolicyCommand): PermissionPolicyRecord

  suspend fun listByTenant(tenantId: UUID): List<PermissionPolicyRecord>

  suspend fun listActiveByRoles(
    tenantId: UUID,
    roleIds: Collection<UUID>,
    at: OffsetDateTime,
  ): List<PermissionPolicyRecord>

  suspend fun expire(id: UUID, validTo: OffsetDateTime): Boolean
}

interface RoleAssignmentRepository {
  suspend fun assign(command: AssignRoleCommand): RoleAssignmentRecord

  suspend fun listByTenant(tenantId: UUID): List<RoleAssignmentRecord>

  suspend fun listActiveByUser(
    tenantId: UUID,
    userId: UUID,
    projectId: UUID?,
    at: OffsetDateTime,
  ): List<RoleAssignmentRecord>

  suspend fun revoke(id: UUID, revokedAt: OffsetDateTime): Boolean
}
