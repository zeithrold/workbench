package one.ztd.workbench.identity.permission

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.identity.permission.model.AuthorizationAction
import one.ztd.workbench.identity.permission.model.PermissionEffect
import one.ztd.workbench.kernel.common.ids.PublicId

enum class AdminScope(val dbValue: String) {
  INSTANCE("instance"),
  TENANT("tenant"),
}

enum class GrantScope(val dbValue: String) {
  INSTANCE("instance"),
  TENANT("tenant"),
  PROJECT("project"),
}

enum class AdminUserStatus(val dbValue: String) {
  ACTIVE("active"),
  REVOKED("revoked"),
}

data class AdminUserRecord(
  val id: UUID,
  val apiId: PublicId,
  val userId: UUID,
  val scope: AdminScope,
  val tenantId: UUID?,
  val status: AdminUserStatus,
  val grantedBy: UUID?,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class AccessGrantRecord(
  val id: UUID,
  val apiId: PublicId,
  val scope: GrantScope,
  val tenantId: UUID?,
  val projectId: UUID?,
  val subjectUserId: UUID,
  val action: AuthorizationAction,
  val resourcePattern: String,
  val effect: PermissionEffect,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime?,
  val grantedBy: UUID?,
  val createdAt: OffsetDateTime,
)

data class CreateAdminUserCommand(
  val userId: UUID,
  val scope: AdminScope,
  val tenantId: UUID? = null,
  val grantedBy: UUID? = null,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime? = null,
)

data class CreateAccessGrantCommand(
  val scope: GrantScope,
  val tenantId: UUID? = null,
  val projectId: UUID? = null,
  val subjectUserId: UUID,
  val action: AuthorizationAction,
  val resourcePattern: String = "*",
  val effect: PermissionEffect = PermissionEffect.ALLOW,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime? = null,
  val grantedBy: UUID? = null,
)

interface AccessGrantRepository {
  suspend fun create(command: CreateAccessGrantCommand): AccessGrantRecord

  suspend fun findById(id: UUID): AccessGrantRecord?

  suspend fun findByApiId(apiId: String): AccessGrantRecord?

  suspend fun listBySubject(
    subjectUserId: UUID,
    scope: GrantScope?,
    tenantId: UUID?,
    projectId: UUID?,
  ): List<AccessGrantRecord>

  suspend fun listActiveForSubject(
    subjectUserId: UUID,
    scope: GrantScope,
    tenantId: UUID?,
    projectId: UUID?,
    at: OffsetDateTime,
  ): List<AccessGrantRecord>

  suspend fun listByTenant(tenantId: UUID): List<AccessGrantRecord>

  suspend fun listInstanceGrants(): List<AccessGrantRecord>

  suspend fun expire(id: UUID, validTo: OffsetDateTime): Boolean

  suspend fun expireByTenant(tenantId: UUID, expiredAt: OffsetDateTime): Int
}
