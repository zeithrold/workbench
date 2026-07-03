package doa.ink.workbench.core.identity.model

import doa.ink.workbench.core.common.ids.PublicId
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class TenantMemberRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val userId: UUID,
  val status: TenantMemberStatus,
  val joinedAt: OffsetDateTime?,
  val invitedBy: UUID?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class LoginMethodDefinitionRecord(
  val id: UUID,
  val apiId: PublicId,
  val code: String,
  val kind: LoginMethodKind,
  val name: String,
  val isBuiltin: Boolean,
  val isEnabledGlobally: Boolean,
  val configSchema: JsonElement = JsonObject(emptyMap()),
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class TenantLoginMethodSettingRecord(
  val id: UUID,
  val tenantId: UUID,
  val loginMethodId: UUID,
  val isEnabled: Boolean,
  val allowSignup: Boolean,
  val displayOrder: Int,
  val config: JsonElement = JsonObject(emptyMap()),
  val secretRef: String?,
  val createdBy: UUID?,
  val updatedBy: UUID?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class LoginAccountRecord(
  val id: UUID,
  val apiId: PublicId,
  val loginMethodId: UUID,
  val subject: String,
  val normalizedSubject: String,
  val displayName: String?,
  val lastUsedAt: OffsetDateTime?,
  val disabledAt: OffsetDateTime?,
  val disabledBy: UUID?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class LoginAccountParameterRecord(
  val id: UUID,
  val loginAccountId: UUID,
  val parameterKey: LoginAccountParameterKey,
  val parameterValue: String?,
  val secretRef: String?,
  val metadata: JsonElement = JsonObject(emptyMap()),
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class UserLoginAccountRecord(
  val id: UUID,
  val userId: UUID,
  val loginAccountId: UUID,
  val linkedBy: UUID?,
  val linkedAt: OffsetDateTime,
  val unlinkedAt: OffsetDateTime?,
)

data class AuthEventRecord(
  val id: UUID,
  val authEventId: PublicId,
  val tenantId: UUID?,
  val userId: UUID?,
  val loginAccountId: UUID?,
  val loginMethodId: UUID?,
  val eventType: AuthEventType,
  val result: AuditEventResult,
  val failureReason: String?,
  val ipAddress: String?,
  val userAgent: String?,
  val metadata: JsonElement = JsonObject(emptyMap()),
  val occurredAt: OffsetDateTime,
)

data class AuthSessionRecord(
  val id: UUID,
  val sessionHash: String,
  val userId: UUID,
  val loginAccountId: UUID,
  val activeTenantId: UUID?,
  val expiresAt: OffsetDateTime,
  val revokedAt: OffsetDateTime?,
  val lastUsedAt: OffsetDateTime?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class BearerTokenRecord(
  val id: UUID,
  val apiId: PublicId,
  val tokenHash: String,
  val userId: UUID,
  val loginAccountId: UUID,
  val tenantId: UUID?,
  val name: String?,
  val scopes: Set<String>,
  val createdBy: UUID?,
  val expiresAt: OffsetDateTime,
  val revokedAt: OffsetDateTime?,
  val lastUsedAt: OffsetDateTime?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class IssuedCredential(
  val id: UUID,
  val apiId: PublicId?,
  val secret: String,
  val expiresAt: OffsetDateTime,
)

data class LoginCommand(
  val method: LoginMethodKind,
  val loginMethodId: String? = null,
  val tenantId: String? = null,
  val subject: String? = null,
  val password: String? = null,
  val token: String? = null,
  val email: String? = null,
  val issueBearerToken: Boolean = false,
  val ipAddress: String? = null,
  val userAgent: String? = null,
)

data class AuthenticationResult(
  val principal: AuthenticatedPrincipal,
  val session: IssuedCredential,
  val bearerToken: IssuedCredential?,
)

data class CreateAuthSessionCommand(
  val sessionHash: String,
  val userId: UUID,
  val loginAccountId: UUID,
  val expiresAt: OffsetDateTime,
  val activeTenantId: UUID? = null,
)

data class CreateBearerTokenCommand(
  val tokenHash: String,
  val userId: UUID,
  val loginAccountId: UUID,
  val expiresAt: OffsetDateTime,
  val tenantId: UUID? = null,
  val name: String? = null,
  val scopes: Set<String> = emptySet(),
  val createdBy: UUID? = null,
)

enum class AuthenticationFailureReason(val eventValue: String) {
  INVALID_CREDENTIALS("invalid_credentials"),
  LOGIN_METHOD_DISABLED("login_method_disabled"),
  ACCOUNT_DISABLED("account_disabled"),
  TENANT_MEMBER_INACTIVE("tenant_member_inactive"),
}

data class CreateUserCommand(
  val displayName: String,
  val primaryEmail: String?,
  val avatarUrl: String? = null,
  val timezone: String? = null,
  val locale: String? = null,
)

data class CreateTenantMemberCommand(
  val tenantId: UUID,
  val userId: UUID,
  val status: TenantMemberStatus = TenantMemberStatus.ACTIVE,
  val joinedAt: OffsetDateTime? = null,
  val invitedBy: UUID? = null,
)

data class CreateTenantCommand(
  val name: String,
  val slug: String,
  val timezone: String = "UTC",
  val locale: String = "en-US",
  val status: TenantStatus = TenantStatus.ACTIVE,
)

data class UpdateTenantCommand(
  val tenantId: UUID,
  val name: String? = null,
  val slug: String? = null,
  val timezone: String? = null,
  val locale: String? = null,
  val status: TenantStatus? = null,
)

data class BootstrapInstanceAdminCommand(
  val displayName: String,
  val email: String,
  val password: String,
  val setupToken: String? = null,
  val ipAddress: String? = null,
  val userAgent: String? = null,
)

data class CreateLoginMethodDefinitionCommand(
  val code: String,
  val kind: LoginMethodKind,
  val name: String,
  val isBuiltin: Boolean = false,
  val isEnabledGlobally: Boolean = true,
  val configSchema: JsonElement = JsonObject(emptyMap()),
)

data class CreateTenantLoginMethodSettingCommand(
  val tenantId: UUID,
  val loginMethodId: UUID,
  val isEnabled: Boolean = true,
  val allowSignup: Boolean = false,
  val displayOrder: Int = 100,
  val config: JsonElement = JsonObject(emptyMap()),
  val secretRef: String? = null,
  val createdBy: UUID? = null,
  val updatedBy: UUID? = null,
)

data class CreateLoginAccountCommand(
  val loginMethodId: UUID,
  val subject: String,
  val normalizedSubject: String,
  val displayName: String? = null,
)

data class UpsertLoginAccountParameterCommand(
  val loginAccountId: UUID,
  val parameterKey: LoginAccountParameterKey,
  val parameterValue: String? = null,
  val secretRef: String? = null,
  val metadata: JsonElement = JsonObject(emptyMap()),
)

data class LinkUserLoginAccountCommand(
  val userId: UUID,
  val loginAccountId: UUID,
  val linkedBy: UUID? = null,
)

data class CreateAuthEventCommand(
  val tenantId: UUID? = null,
  val userId: UUID? = null,
  val loginAccountId: UUID? = null,
  val loginMethodId: UUID? = null,
  val eventType: AuthEventType,
  val result: AuditEventResult,
  val failureReason: String? = null,
  val ipAddress: String? = null,
  val userAgent: String? = null,
  val metadata: JsonElement = JsonObject(emptyMap()),
)

data class AuthLoginStateRecord(
  val id: UUID,
  val stateHash: String,
  val tenantId: UUID,
  val loginMethodId: UUID,
  val redirectUri: String,
  val pkceVerifier: String?,
  val returnUrl: String?,
  val expiresAt: OffsetDateTime,
  val consumedAt: OffsetDateTime?,
  val createdAt: OffsetDateTime,
)

data class CreateAuthLoginStateCommand(
  val stateHash: String,
  val tenantId: UUID,
  val loginMethodId: UUID,
  val redirectUri: String,
  val pkceVerifier: String?,
  val returnUrl: String?,
  val expiresAt: OffsetDateTime,
)

data class MagicLinkTokenRecord(
  val id: UUID,
  val tokenHash: String,
  val loginMethodId: UUID,
  val tenantId: UUID,
  val normalizedSubject: String,
  val expiresAt: OffsetDateTime,
  val consumedAt: OffsetDateTime?,
  val createdAt: OffsetDateTime,
)

data class TenantLoginOption(
  val tenant: doa.ink.workbench.core.common.summary.TenantSummary,
  val loginMethod: doa.ink.workbench.core.common.summary.LoginMethodSummary,
)

data class AuthenticatedIdentity(
  val user: UserRecord,
  val loginAccount: LoginAccountRecord,
)
