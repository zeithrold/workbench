package doa.ink.workbench.data.persistence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

object TenantsTable : Table("tenants") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val name = text("name")
  val slug = text("slug").uniqueIndex()
  val timezone = text("timezone")
  val locale = text("locale")
  val archivedAt = timestampWithTimeZone("archived_at").nullable()
  val archivedBy = uuid("archived_by").references(UsersTable.id).nullable()
  val deletedAt = timestampWithTimeZone("deleted_at").nullable()
  val deletedBy = uuid("deleted_by").references(UsersTable.id).nullable()
  val deleteReason = text("delete_reason").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object UsersTable : Table("users") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val displayName = text("display_name")
  val primaryEmail = text("primary_email").nullable()
  val avatarUrl = text("avatar_url").nullable()
  val timezone = text("timezone").nullable()
  val locale = text("locale").nullable()
  val archivedAt = timestampWithTimeZone("archived_at").nullable()
  val archivedBy = uuid("archived_by").references(id).nullable()
  val deletedAt = timestampWithTimeZone("deleted_at").nullable()
  val deletedBy = uuid("deleted_by").references(id).nullable()
  val deleteReason = text("delete_reason").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object TenantMembersTable : Table("tenant_members") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val userId = uuid("user_id").references(UsersTable.id)
  val status = text("status")
  val joinedAt = timestampWithTimeZone("joined_at").nullable()
  val invitedBy = uuid("invited_by").references(UsersTable.id).nullable()
  val archivedAt = timestampWithTimeZone("archived_at").nullable()
  val archivedBy = uuid("archived_by").references(UsersTable.id).nullable()
  val deletedAt = timestampWithTimeZone("deleted_at").nullable()
  val deletedBy = uuid("deleted_by").references(UsersTable.id).nullable()
  val deleteReason = text("delete_reason").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object LoginMethodDefinitionsTable : Table("login_method_definitions") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val code = text("code").uniqueIndex()
  val kind = text("kind")
  val name = text("name")
  val isBuiltin = bool("is_builtin")
  val isEnabledGlobally = bool("is_enabled_globally")
  val configSchema = jsonb("config_schema", Json.Default, JsonElement.serializer())
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object TenantLoginMethodSettingsTable : Table("tenant_login_method_settings") {
  val id = uuid("id")
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val loginMethodId = uuid("login_method_id").references(LoginMethodDefinitionsTable.id)
  val isEnabled = bool("is_enabled")
  val allowSignup = bool("allow_signup")
  val displayOrder = integer("display_order")
  val config = jsonb("config", Json.Default, JsonElement.serializer())
  val secretRef = text("secret_ref").nullable()
  val createdBy = uuid("created_by").references(UsersTable.id).nullable()
  val updatedBy = uuid("updated_by").references(UsersTable.id).nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object LoginAccountsTable : Table("login_accounts") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val loginMethodId = uuid("login_method_id").references(LoginMethodDefinitionsTable.id)
  val subject = text("subject")
  val normalizedSubject = text("normalized_subject")
  val displayName = text("display_name").nullable()
  val lastUsedAt = timestampWithTimeZone("last_used_at").nullable()
  val disabledAt = timestampWithTimeZone("disabled_at").nullable()
  val disabledBy = uuid("disabled_by").references(UsersTable.id).nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object LoginAccountParametersTable : Table("login_account_parameters") {
  val id = uuid("id")
  val loginAccountId = uuid("login_account_id").references(LoginAccountsTable.id)
  val parameterKey = text("parameter_key")
  val parameterValue = text("parameter_value").nullable()
  val secretRef = text("secret_ref").nullable()
  val metadata = jsonb("metadata", Json.Default, JsonElement.serializer())
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object UserLoginAccountsTable : Table("user_login_accounts") {
  val id = uuid("id")
  val userId = uuid("user_id").references(UsersTable.id)
  val loginAccountId = uuid("login_account_id").references(LoginAccountsTable.id).uniqueIndex()
  val linkedBy = uuid("linked_by").references(UsersTable.id).nullable()
  val linkedAt = timestampWithTimeZone("linked_at")
  val unlinkedAt = timestampWithTimeZone("unlinked_at").nullable()
  override val primaryKey = PrimaryKey(id)
}

object AuthSessionsTable : Table("auth_sessions") {
  val id = uuid("id")
  val sessionHash = text("session_hash").uniqueIndex()
  val userId = uuid("user_id").references(UsersTable.id)
  val loginAccountId = uuid("login_account_id").references(LoginAccountsTable.id)
  val activeTenantId = uuid("active_tenant_id").references(TenantsTable.id).nullable()
  val expiresAt = timestampWithTimeZone("expires_at")
  val revokedAt = timestampWithTimeZone("revoked_at").nullable()
  val lastUsedAt = timestampWithTimeZone("last_used_at").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object BearerTokensTable : Table("bearer_tokens") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tokenHash = text("token_hash").uniqueIndex()
  val userId = uuid("user_id").references(UsersTable.id)
  val loginAccountId = uuid("login_account_id").references(LoginAccountsTable.id)
  val tenantId = uuid("tenant_id").references(TenantsTable.id).nullable()
  val name = text("name").nullable()
  val scopes = jsonb("scopes", Json.Default, JsonElement.serializer())
  val createdBy = uuid("created_by").references(UsersTable.id).nullable()
  val expiresAt = timestampWithTimeZone("expires_at")
  val revokedAt = timestampWithTimeZone("revoked_at").nullable()
  val lastUsedAt = timestampWithTimeZone("last_used_at").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object AuthLoginStatesTable : Table("auth_login_states") {
  val id = uuid("id")
  val stateHash = text("state_hash").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val loginMethodId = uuid("login_method_id").references(LoginMethodDefinitionsTable.id)
  val redirectUri = text("redirect_uri")
  val pkceVerifier = text("pkce_verifier").nullable()
  val returnUrl = text("return_url").nullable()
  val expiresAt = timestampWithTimeZone("expires_at")
  val consumedAt = timestampWithTimeZone("consumed_at").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  override val primaryKey = PrimaryKey(id)
}

object MagicLinkTokensTable : Table("magic_link_tokens") {
  val id = uuid("id")
  val tokenHash = text("token_hash").uniqueIndex()
  val loginMethodId = uuid("login_method_id").references(LoginMethodDefinitionsTable.id)
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val normalizedSubject = text("normalized_subject")
  val expiresAt = timestampWithTimeZone("expires_at")
  val consumedAt = timestampWithTimeZone("consumed_at").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  override val primaryKey = PrimaryKey(id)
}
