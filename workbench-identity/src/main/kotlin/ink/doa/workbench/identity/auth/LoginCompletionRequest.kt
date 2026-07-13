package ink.doa.workbench.identity.auth

import ink.doa.workbench.identity.model.AuthenticatedIdentity
import java.util.UUID

data class LoginCompletionRequest(
  val identity: AuthenticatedIdentity,
  val issueBearerToken: Boolean,
  val ipAddress: String?,
  val userAgent: String?,
  val tenantIdForAudit: UUID? = null,
  val activeTenantId: UUID? = null,
)
