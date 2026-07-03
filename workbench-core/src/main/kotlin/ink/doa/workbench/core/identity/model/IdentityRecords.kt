package ink.doa.workbench.core.identity.model

import ink.doa.workbench.core.common.ids.PublicId
import java.time.OffsetDateTime
import java.util.UUID

data class TenantRecord(
  val id: UUID,
  val apiId: PublicId,
  val slug: String,
  val name: String,
  val timezone: String = "UTC",
  val locale: String = "en-US",
  val status: TenantStatus = TenantStatus.ACTIVE,
  val createdAt: OffsetDateTime? = null,
  val updatedAt: OffsetDateTime? = null,
)

data class UserRecord(
  val id: UUID,
  val apiId: PublicId,
  val displayName: String,
  val primaryEmail: String?,
  val avatarUrl: String? = null,
  val timezone: String? = null,
  val locale: String? = null,
  val createdAt: OffsetDateTime? = null,
  val updatedAt: OffsetDateTime? = null,
)

data class AuthenticatedPrincipal(
  val user: UserRecord,
  val loginAccountId: UUID?,
  val sessionId: String?,
  val bearerTokenId: String?,
  val credentialType: CredentialType =
    if (bearerTokenId != null) CredentialType.BEARER_TOKEN else CredentialType.SESSION,
  val tenantId: UUID? = null,
  val credentialScopes: Set<String> = emptySet(),
)

enum class CredentialType {
  SESSION,
  BEARER_TOKEN,
}

enum class TenantMemberStatus(val dbValue: String) {
  ACTIVE("active"),
  INVITED("invited"),
  SUSPENDED("suspended"),
  REMOVED("removed"),
}

enum class TenantStatus(val dbValue: String) {
  ACTIVE("active"),
  PENDING_ACTIVATION("pending_activation"),
  DESTROYING("destroying"),
}

enum class InvitationType(val dbValue: String) {
  TENANT_ADMIN("tenant_admin"),
  TENANT_MEMBER("tenant_member"),
}

enum class LoginMethodKind(val dbValue: String) {
  PASSWORD("password"),
  EMAIL_MAGIC_LINK("email_magic_link"),
  OAUTH2("oauth2"),
  OIDC("oidc"),
  LDAP("ldap"),
  SAML("saml"),
  API_TOKEN("api_token"),
}

enum class AuthEventType(val dbValue: String) {
  LOGIN_SUCCESS("login_success"),
  LOGIN_FAILURE("login_failure"),
  LOGOUT("logout"),
  PASSWORD_CHANGED("password_changed"),
  PASSWORD_RESET_REQUESTED("password_reset_requested"),
  CREDENTIAL_LINKED("credential_linked"),
  CREDENTIAL_UNLINKED("credential_unlinked"),
  TOKEN_CREATED("token_created"),
  TOKEN_REVOKED("token_revoked"),
}

enum class AuditEventResult(val dbValue: String) {
  SUCCESS("success"),
  FAILURE("failure"),
}

@JvmInline
value class LoginAccountParameterKey(val value: String) {
  init {
    require(value.matches(Regex("^[a-z][a-z0-9_]*$"))) {
      "Login account parameter key must be lower snake case."
    }
  }

  companion object {
    val PasswordHash = LoginAccountParameterKey("password_hash")
    val ApiTokenHash = LoginAccountParameterKey("api_token_hash")
  }
}
