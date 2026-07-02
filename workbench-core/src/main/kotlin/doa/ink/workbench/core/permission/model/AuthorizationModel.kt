package doa.ink.workbench.core.permission.model

import doa.ink.workbench.core.identity.model.CredentialType
import java.time.Instant
import java.util.UUID

data class AuthorizationRequest(
  val subject: AuthorizationSubject,
  val tenantId: UUID,
  val action: AuthorizationAction,
  val resource: AuthorizationResource,
  val environment: AuthorizationEnvironment,
)

data class AuthorizationSubject(
  val userId: UUID,
  val loginAccountId: UUID?,
  val credentialType: CredentialType,
  val credentialId: String?,
  val credentialTenantId: UUID?,
  val credentialScopes: Set<String>,
)

@JvmInline
value class AuthorizationAction(val code: String) {
  init {
    require(code.matches(Regex("^[a-z]+(\\.[a-z]+)+$"))) {
      "Authorization action must be dot-separated lower-case words."
    }
  }
}

data class AuthorizationResource(
  val type: String,
  val id: String? = null,
  val tenantId: UUID,
  val projectId: UUID? = null,
  val attributes: Map<String, String> = emptyMap(),
) {
  val canonical: String
    get() = listOfNotNull(type, id ?: "*").joinToString(":")
}

data class AuthorizationEnvironment(
  val requestId: String?,
  val occurredAt: Instant,
  val attributes: Map<String, String> = emptyMap(),
)

sealed interface AuthorizationDecision {
  val reason: DecisionReason

  data class Allow(override val reason: DecisionReason) : AuthorizationDecision

  data class Deny(override val reason: DecisionReason) : AuthorizationDecision
}

data class DecisionReason(
  val code: String,
  val message: String,
  val policyId: UUID? = null,
  val roleId: UUID? = null,
)
