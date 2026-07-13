package ink.doa.workbench.identity.model

import ink.doa.workbench.kernel.common.ids.PublicId
import java.time.OffsetDateTime
import java.util.UUID

data class InvitationRecord(
  val id: UUID,
  val apiId: PublicId,
  val type: InvitationType,
  val tenantId: UUID,
  val email: String,
  val normalizedEmail: String,
  val displayName: String?,
  val tokenHash: String,
  val invitedBy: UUID,
  val expiresAt: OffsetDateTime,
  val consumedAt: OffsetDateTime?,
  val createdAt: OffsetDateTime?,
)

data class CreateInvitationCommand(
  val type: InvitationType,
  val tenantId: UUID,
  val email: String,
  val normalizedEmail: String,
  val displayName: String?,
  val tokenHash: String,
  val invitedBy: UUID,
  val expiresAt: OffsetDateTime,
)

data class AcceptInvitationCommand(
  val token: String,
  val displayName: String,
  val password: String,
)

sealed interface TenantAdminAssignment {
  data object SelfAssignment : TenantAdminAssignment

  data class UserAssignment(val userId: UUID) : TenantAdminAssignment

  data class EmailInviteAssignment(val email: String, val displayName: String?) :
    TenantAdminAssignment
}

data class CreateTenantWithAdminCommand(
  val name: String,
  val slug: String,
  val timezone: String = "UTC",
  val locale: String = "en-US",
  val adminAssignment: TenantAdminAssignment,
)
