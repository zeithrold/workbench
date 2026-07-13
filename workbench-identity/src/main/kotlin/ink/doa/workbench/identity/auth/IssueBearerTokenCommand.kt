package ink.doa.workbench.identity.auth

import java.time.OffsetDateTime
import java.util.UUID

data class IssueBearerTokenCommand(
  val userId: UUID,
  val loginAccountId: UUID,
  val tenantId: UUID?,
  val name: String?,
  val scopes: Set<String>,
  val createdBy: UUID?,
  val now: OffsetDateTime,
)

data class CreateManagedBearerTokenCommand(
  val userId: UUID,
  val loginAccountId: UUID,
  val tenantId: UUID?,
  val name: String?,
  val scopes: Set<String>,
  val ipAddress: String?,
  val userAgent: String?,
)
