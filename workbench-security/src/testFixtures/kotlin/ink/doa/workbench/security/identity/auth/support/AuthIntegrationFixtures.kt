package ink.doa.workbench.security.identity.auth.support

import dasniko.testcontainers.keycloak.KeycloakContainer
import ink.doa.workbench.data.persistence.postgres.identity.TenantsTable
import ink.doa.workbench.data.repository.identity.ExposedLoginAccountStore
import ink.doa.workbench.data.repository.identity.ExposedLoginMethodRepository
import ink.doa.workbench.data.repository.identity.ExposedTenantLoginMethodSettingRepository
import ink.doa.workbench.data.repository.identity.ExposedTenantMemberRepository
import ink.doa.workbench.data.repository.identity.ExposedUserLoginAccountRepository
import ink.doa.workbench.data.repository.identity.ExposedUserRepository
import ink.doa.workbench.identity.auth.SecretResolver
import ink.doa.workbench.identity.model.CreateLoginAccountCommand
import ink.doa.workbench.identity.model.CreateLoginMethodDefinitionCommand
import ink.doa.workbench.identity.model.CreateTenantLoginMethodSettingCommand
import ink.doa.workbench.identity.model.CreateTenantMemberCommand
import ink.doa.workbench.identity.model.CreateUserCommand
import ink.doa.workbench.identity.model.LinkUserLoginAccountCommand
import ink.doa.workbench.identity.model.LoginMethodKind
import ink.doa.workbench.identity.model.TenantMemberStatus
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.PostgresTestDatabaseLease
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

data class TenantSeed(
  val tenantApiId: String,
  val tenantId: UUID,
)

data class LdapAuthFixture(
  val tenant: TenantSeed,
  val loginMethodApiId: String,
  val linkedUserId: UUID,
)

data class FederatedAuthFixture(
  val tenant: TenantSeed,
  val oidcLoginMethodApiId: String,
  val oauth2LoginMethodApiId: String,
  val oidcUserId: UUID,
  val oauth2UserId: UUID,
)

data class AuthIntegrationFixture(
  val tenantApiId: String,
  val tenantId: UUID,
  val ldapLoginMethodApiId: String,
  val oidcLoginMethodApiId: String,
  val oauth2LoginMethodApiId: String,
  val ldapUserId: UUID,
  val oidcUserId: UUID,
  val oauth2UserId: UUID,
)

class MapSecretResolver(private val secrets: Map<String, String>) : SecretResolver {
  override fun resolve(secretRef: String): String? = secrets[secretRef]
}

object AuthIntegrationFixtures {
  private data class LoginRepositories(
    val loginMethods: ExposedLoginMethodRepository,
    val tenantLoginSettings: ExposedTenantLoginMethodSettingRepository,
    val loginAccounts: ExposedLoginAccountStore,
    val userLoginAccounts: ExposedUserLoginAccountRepository,
  )

  private fun loginRepositories(database: Database): LoginRepositories =
    LoginRepositories(
      loginMethods = ExposedLoginMethodRepository(database),
      tenantLoginSettings = ExposedTenantLoginMethodSettingRepository(database),
      loginAccounts = ExposedLoginAccountStore(database),
      userLoginAccounts = ExposedUserLoginAccountRepository(database),
    )

  fun openSpecDatabase(): PostgresTestDatabaseLease =
    WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full)

  suspend fun seedLdapFixture(
    database: Database,
    ldap: InMemoryLdapTestServer,
  ): LdapAuthFixture {
    val tenant = seedTenant(database)
    val methodCode = uniqueMethodCode("ldap")
    val users = ExposedUserRepository(database)
    val members = ExposedTenantMemberRepository(database)
    val repos = loginRepositories(database)
    val now = OffsetDateTime.now(ZoneOffset.UTC)

    val user = users.create(CreateUserCommand("LDAP User", "testuser@example.test"))
    members.create(
      CreateTenantMemberCommand(
        tenantId = tenant.tenantId,
        userId = user.id,
        status = TenantMemberStatus.ACTIVE,
        joinedAt = now,
      )
    )

    val method =
      repos.loginMethods.createLoginMethod(
        CreateLoginMethodDefinitionCommand(
          code = methodCode,
          kind = LoginMethodKind.LDAP,
          name = "LDAP",
        )
      )
    repos.tenantLoginSettings.createTenantSetting(
      CreateTenantLoginMethodSettingCommand(
        tenantId = tenant.tenantId,
        loginMethodId = method.id,
        config = ldapConfig(ldap),
      )
    )
    val loginAccount =
      repos.loginAccounts.createLoginAccount(
        CreateLoginAccountCommand(
          loginMethodId = method.id,
          subject = InMemoryLdapTestServer.TEST_USER,
          normalizedSubject = InMemoryLdapTestServer.TEST_USER,
          displayName = "LDAP User",
        )
      )
    repos.userLoginAccounts.linkUser(LinkUserLoginAccountCommand(user.id, loginAccount.id, user.id))

    return LdapAuthFixture(
      tenant = tenant,
      loginMethodApiId = method.apiId.value,
      linkedUserId = user.id,
    )
  }

  suspend fun seedFederatedFixture(
    database: Database,
    keycloakContainer: KeycloakContainer,
  ): FederatedAuthFixture {
    val tenant = seedTenant(database)
    val oidcMethodCode = uniqueMethodCode("oidc")
    val oauth2MethodCode = uniqueMethodCode("oauth2")
    val users = ExposedUserRepository(database)
    val members = ExposedTenantMemberRepository(database)
    val repos = loginRepositories(database)
    val now = OffsetDateTime.now(ZoneOffset.UTC)

    val oidcUser = users.create(CreateUserCommand("OIDC User", KeycloakTestContainer.OIDC_USER))
    val oauth2User =
      users.create(CreateUserCommand("OAuth User", KeycloakTestContainer.OAUTH2_USER))
    listOf(oidcUser, oauth2User).forEach { user ->
      members.create(
        CreateTenantMemberCommand(
          tenantId = tenant.tenantId,
          userId = user.id,
          status = TenantMemberStatus.ACTIVE,
          joinedAt = now,
        )
      )
    }

    val oidcMethod =
      repos.loginMethods.createLoginMethod(
        CreateLoginMethodDefinitionCommand(
          code = oidcMethodCode,
          kind = LoginMethodKind.OIDC,
          name = "OIDC",
        )
      )
    repos.tenantLoginSettings.createTenantSetting(
      CreateTenantLoginMethodSettingCommand(
        tenantId = tenant.tenantId,
        loginMethodId = oidcMethod.id,
        secretRef = KeycloakTestContainer.OIDC_SECRET_REF,
        config = oidcConfig(keycloakContainer),
      )
    )
    val oidcAccount =
      repos.loginAccounts.createLoginAccount(
        CreateLoginAccountCommand(
          loginMethodId = oidcMethod.id,
          subject = KeycloakTestContainer.OIDC_USER,
          normalizedSubject = KeycloakTestContainer.OIDC_USER.lowercase(),
          displayName = "OIDC User",
        )
      )
    repos.userLoginAccounts.linkUser(
      LinkUserLoginAccountCommand(oidcUser.id, oidcAccount.id, oidcUser.id)
    )

    val oauth2Method =
      repos.loginMethods.createLoginMethod(
        CreateLoginMethodDefinitionCommand(
          code = oauth2MethodCode,
          kind = LoginMethodKind.OAUTH2,
          name = "OAuth2",
        )
      )
    repos.tenantLoginSettings.createTenantSetting(
      CreateTenantLoginMethodSettingCommand(
        tenantId = tenant.tenantId,
        loginMethodId = oauth2Method.id,
        secretRef = KeycloakTestContainer.OAUTH2_SECRET_REF,
        config = oauth2Config(keycloakContainer),
      )
    )
    val oauth2Account =
      repos.loginAccounts.createLoginAccount(
        CreateLoginAccountCommand(
          loginMethodId = oauth2Method.id,
          subject = KeycloakTestContainer.OAUTH2_USER,
          normalizedSubject = KeycloakTestContainer.OAUTH2_USER.lowercase(),
          displayName = "OAuth User",
        )
      )
    repos.userLoginAccounts.linkUser(
      LinkUserLoginAccountCommand(oauth2User.id, oauth2Account.id, oauth2User.id)
    )

    return FederatedAuthFixture(
      tenant = tenant,
      oidcLoginMethodApiId = oidcMethod.apiId.value,
      oauth2LoginMethodApiId = oauth2Method.apiId.value,
      oidcUserId = oidcUser.id,
      oauth2UserId = oauth2User.id,
    )
  }

  suspend fun seedAuthFixture(
    database: Database,
    ldap: InMemoryLdapTestServer,
    keycloakContainer: KeycloakContainer,
  ): AuthIntegrationFixture {
    val tenant = seedTenant(database)
    val users = ExposedUserRepository(database)
    val members = ExposedTenantMemberRepository(database)
    val repos = loginRepositories(database)
    val now = OffsetDateTime.now(ZoneOffset.UTC)

    val ldapUser = users.create(CreateUserCommand("LDAP User", "testuser@example.test"))
    val oidcUser = users.create(CreateUserCommand("OIDC User", KeycloakTestContainer.OIDC_USER))
    val oauth2User =
      users.create(CreateUserCommand("OAuth User", KeycloakTestContainer.OAUTH2_USER))
    listOf(ldapUser, oidcUser, oauth2User).forEach { user ->
      members.create(
        CreateTenantMemberCommand(
          tenantId = tenant.tenantId,
          userId = user.id,
          status = TenantMemberStatus.ACTIVE,
          joinedAt = now,
        )
      )
    }

    val ldapMethod =
      repos.loginMethods.createLoginMethod(
        CreateLoginMethodDefinitionCommand(
          code = uniqueMethodCode("ldap"),
          kind = LoginMethodKind.LDAP,
          name = "LDAP",
        )
      )
    repos.tenantLoginSettings.createTenantSetting(
      CreateTenantLoginMethodSettingCommand(
        tenantId = tenant.tenantId,
        loginMethodId = ldapMethod.id,
        config = ldapConfig(ldap),
      )
    )
    val ldapAccount =
      repos.loginAccounts.createLoginAccount(
        CreateLoginAccountCommand(
          loginMethodId = ldapMethod.id,
          subject = InMemoryLdapTestServer.TEST_USER,
          normalizedSubject = InMemoryLdapTestServer.TEST_USER,
          displayName = "LDAP User",
        )
      )
    repos.userLoginAccounts.linkUser(
      LinkUserLoginAccountCommand(ldapUser.id, ldapAccount.id, ldapUser.id)
    )

    val oidcMethod =
      repos.loginMethods.createLoginMethod(
        CreateLoginMethodDefinitionCommand(
          code = uniqueMethodCode("oidc"),
          kind = LoginMethodKind.OIDC,
          name = "OIDC",
        )
      )
    repos.tenantLoginSettings.createTenantSetting(
      CreateTenantLoginMethodSettingCommand(
        tenantId = tenant.tenantId,
        loginMethodId = oidcMethod.id,
        secretRef = KeycloakTestContainer.OIDC_SECRET_REF,
        config = oidcConfig(keycloakContainer),
      )
    )
    val oidcAccount =
      repos.loginAccounts.createLoginAccount(
        CreateLoginAccountCommand(
          loginMethodId = oidcMethod.id,
          subject = KeycloakTestContainer.OIDC_USER,
          normalizedSubject = KeycloakTestContainer.OIDC_USER.lowercase(),
          displayName = "OIDC User",
        )
      )
    repos.userLoginAccounts.linkUser(
      LinkUserLoginAccountCommand(oidcUser.id, oidcAccount.id, oidcUser.id)
    )

    val oauth2Method =
      repos.loginMethods.createLoginMethod(
        CreateLoginMethodDefinitionCommand(
          code = uniqueMethodCode("oauth2"),
          kind = LoginMethodKind.OAUTH2,
          name = "OAuth2",
        )
      )
    repos.tenantLoginSettings.createTenantSetting(
      CreateTenantLoginMethodSettingCommand(
        tenantId = tenant.tenantId,
        loginMethodId = oauth2Method.id,
        secretRef = KeycloakTestContainer.OAUTH2_SECRET_REF,
        config = oauth2Config(keycloakContainer),
      )
    )
    val oauth2Account =
      repos.loginAccounts.createLoginAccount(
        CreateLoginAccountCommand(
          loginMethodId = oauth2Method.id,
          subject = KeycloakTestContainer.OAUTH2_USER,
          normalizedSubject = KeycloakTestContainer.OAUTH2_USER.lowercase(),
          displayName = "OAuth User",
        )
      )
    repos.userLoginAccounts.linkUser(
      LinkUserLoginAccountCommand(oauth2User.id, oauth2Account.id, oauth2User.id)
    )

    return AuthIntegrationFixture(
      tenantApiId = tenant.tenantApiId,
      tenantId = tenant.tenantId,
      ldapLoginMethodApiId = ldapMethod.apiId.value,
      oidcLoginMethodApiId = oidcMethod.apiId.value,
      oauth2LoginMethodApiId = oauth2Method.apiId.value,
      ldapUserId = ldapUser.id,
      oidcUserId = oidcUser.id,
      oauth2UserId = oauth2User.id,
    )
  }

  fun keycloakSecrets(): Map<String, String> =
    mapOf(
      KeycloakTestContainer.OIDC_SECRET_REF to KeycloakTestContainer.OIDC_CLIENT_SECRET,
      KeycloakTestContainer.OAUTH2_SECRET_REF to KeycloakTestContainer.OAUTH2_CLIENT_SECRET,
    )

  fun ldapConfig(ldap: InMemoryLdapTestServer): JsonObject =
    JsonObject(
      mapOf(
        "host" to JsonPrimitive(ldap.host),
        "port" to JsonPrimitive(ldap.port.toString()),
        "base_dn" to JsonPrimitive(InMemoryLdapTestServer.BASE_DN),
      )
    )

  private fun oidcConfig(keycloakContainer: KeycloakContainer): JsonObject =
    JsonObject(
      mapOf(
        "client_id" to JsonPrimitive(KeycloakTestContainer.OIDC_CLIENT_ID),
        "authorization_endpoint" to
          JsonPrimitive(KeycloakTestContainer.authorizationEndpoint(keycloakContainer)),
        "token_endpoint" to JsonPrimitive(KeycloakTestContainer.tokenEndpoint(keycloakContainer)),
        "userinfo_endpoint" to
          JsonPrimitive(KeycloakTestContainer.userinfoEndpoint(keycloakContainer)),
        "scope" to JsonPrimitive("openid profile email"),
      )
    )

  private fun oauth2Config(keycloakContainer: KeycloakContainer): JsonObject =
    JsonObject(
      mapOf(
        "client_id" to JsonPrimitive(KeycloakTestContainer.OAUTH2_CLIENT_ID),
        "authorization_endpoint" to
          JsonPrimitive(KeycloakTestContainer.authorizationEndpoint(keycloakContainer)),
        "token_endpoint" to JsonPrimitive(KeycloakTestContainer.tokenEndpoint(keycloakContainer)),
        "userinfo_endpoint" to
          JsonPrimitive(KeycloakTestContainer.userinfoEndpoint(keycloakContainer)),
        "scope" to JsonPrimitive("email profile openid"),
      )
    )

  private fun uniqueMethodCode(prefix: String): String =
    "$prefix-${UUID.randomUUID().toString().take(8)}"

  private fun seedTenant(database: Database): TenantSeed {
    val tenantId = UUID.randomUUID()
    val apiId = PublicId.new("ten").value
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    transaction(database) {
      TenantsTable.insert {
        it[id] = tenantId.toKotlinUuid()
        it[TenantsTable.apiId] = apiId
        it[name] = "Auth Integration Tenant"
        it[slug] = "auth-test-${tenantId.toString().take(8)}"
        it[timezone] = "UTC"
        it[locale] = "en-US"
        it[createdAt] = now
        it[updatedAt] = now
      }
    }
    return TenantSeed(tenantApiId = apiId, tenantId = tenantId)
  }
}
