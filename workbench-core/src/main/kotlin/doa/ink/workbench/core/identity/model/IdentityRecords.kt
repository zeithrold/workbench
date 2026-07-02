package doa.ink.workbench.core.identity.model

import doa.ink.workbench.core.common.ids.PublicId
import java.util.UUID

data class TenantRecord(val id: UUID, val apiId: PublicId, val slug: String, val name: String)

data class UserRecord(
  val id: UUID,
  val apiId: PublicId,
  val displayName: String,
  val primaryEmail: String?,
)

data class AuthenticatedPrincipal(
  val user: UserRecord,
  val sessionId: String?,
  val bearerTokenId: String?,
)
