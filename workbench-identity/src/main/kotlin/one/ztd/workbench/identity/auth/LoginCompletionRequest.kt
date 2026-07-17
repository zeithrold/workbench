package one.ztd.workbench.identity.auth

import java.util.UUID
import one.ztd.workbench.identity.model.AuthenticatedIdentity

data class LoginCompletionRequest(
  val identity: AuthenticatedIdentity,
  val issueBearerToken: Boolean,
  val ipAddress: String?,
  val userAgent: String?,
  val tenantIdForAudit: UUID? = null,
  val activeTenantId: UUID? = null,
)
