package ink.doa.workbench.data.identity

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.model.AuditEventResult
import ink.doa.workbench.core.identity.model.AuthEventType
import ink.doa.workbench.core.identity.model.CreateAuthEventCommand
import ink.doa.workbench.core.identity.model.CreateAuthSessionCommand
import ink.doa.workbench.core.identity.model.CreateBearerTokenCommand
import ink.doa.workbench.core.identity.model.CreateLoginAccountCommand
import ink.doa.workbench.core.identity.model.CreateLoginMethodDefinitionCommand
import ink.doa.workbench.core.identity.model.CreateTenantLoginMethodSettingCommand
import ink.doa.workbench.core.identity.model.CreateTenantMemberCommand
import ink.doa.workbench.core.identity.model.CreateUserCommand
import ink.doa.workbench.core.identity.model.LinkUserLoginAccountCommand
import ink.doa.workbench.core.identity.model.LoginAccountParameterKey
import ink.doa.workbench.core.identity.model.LoginMethodKind
import ink.doa.workbench.core.identity.model.TenantMemberStatus
import ink.doa.workbench.core.identity.model.UpsertLoginAccountParameterCommand
import ink.doa.workbench.data.persistence.LoginAccountsTable
import ink.doa.workbench.data.persistence.TenantsTable
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.testcontainers.containers.PostgreSQLContainer

class ExposedIdentityRepositoriesTest :
  StringSpec({
    "login account binding can resolve a user and stops resolving after unlink or disable" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val users = ExposedUserRepository(database)
        val members = ExposedTenantMemberRepository(database)
        val loginMethods = ExposedLoginMethodRepository(database)
        val tenantLoginSettings = ExposedTenantLoginMethodSettingRepository(database)
        val loginAccounts = ExposedLoginAccountStore(database)
        val userLoginAccounts = ExposedUserLoginAccountRepository(database)
        val loginDiscovery =
          ExposedLoginDiscoveryRepository(database, loginAccounts, userLoginAccounts)

        val user = users.create(CreateUserCommand("Ada", "ada@example.test"))
        val member =
          members.create(
            CreateTenantMemberCommand(
              tenantId = tenantId,
              userId = user.id,
              status = TenantMemberStatus.ACTIVE,
              joinedAt = OffsetDateTime.now(ZoneOffset.UTC),
            )
          )
        val method = loginMethods.findLoginMethodByCode("password").shouldNotBeNull()
        tenantLoginSettings.createTenantSetting(
          CreateTenantLoginMethodSettingCommand(tenantId = tenantId, loginMethodId = method.id)
        )
        val loginAccount =
          loginAccounts.createLoginAccount(
            CreateLoginAccountCommand(
              loginMethodId = method.id,
              subject = "Ada@Example.Test",
              normalizedSubject = "ada@example.test",
              displayName = "Ada",
            )
          )
        val parameter =
          loginAccounts.upsertParameter(
            UpsertLoginAccountParameterCommand(
              loginAccountId = loginAccount.id,
              parameterKey = LoginAccountParameterKey.PasswordHash,
              parameterValue = "hash:v1",
              metadata = JsonObject(mapOf("algorithm" to JsonPrimitive("argon2id"))),
            )
          )
        userLoginAccounts.linkUser(
          LinkUserLoginAccountCommand(user.id, loginAccount.id, linkedBy = user.id)
        )

        member.userId shouldBe user.id
        parameter.parameterKey shouldBe LoginAccountParameterKey.PasswordHash
        loginAccounts
          .findLoginAccountByMethodAndSubject("password", "ada@example.test")
          ?.id shouldBe loginAccount.id
        loginDiscovery.findUserByMethodAndSubject("password", "ada@example.test")?.id shouldBe
          user.id

        userLoginAccounts.unlink(loginAccount.id, OffsetDateTime.now(ZoneOffset.UTC)) shouldBe true
        loginDiscovery.findUserByMethodAndSubject("password", "ada@example.test").shouldBeNull()

        userLoginAccounts.linkUser(
          LinkUserLoginAccountCommand(user.id, loginAccount.id, linkedBy = user.id)
        )
        transaction(database) {
          LoginAccountsTable.update({ LoginAccountsTable.id eq loginAccount.id.toKotlinUuid() }) {
            it[disabledAt] = OffsetDateTime.now(ZoneOffset.UTC)
          }
        }
        loginAccounts
          .findLoginAccountByMethodAndSubject("password", "ada@example.test")
          .shouldBeNull()
      }
    }

    "auth events append and read back by user and login account" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val users = ExposedUserRepository(database)
        val loginMethods = ExposedLoginMethodRepository(database)
        val loginAccounts = ExposedLoginAccountStore(database)
        val events = ExposedAuthEventRepository(database)
        val user = users.create(CreateUserCommand("Grace", "grace@example.test"))
        val method =
          loginMethods.createLoginMethod(
            CreateLoginMethodDefinitionCommand("api_token", LoginMethodKind.API_TOKEN, "API Token")
          )
        val loginAccount =
          loginAccounts.createLoginAccount(
            CreateLoginAccountCommand(method.id, "token-owner", "token-owner", "Grace token")
          )

        val event =
          events.append(
            CreateAuthEventCommand(
              tenantId = tenantId,
              userId = user.id,
              loginAccountId = loginAccount.id,
              loginMethodId = method.id,
              eventType = AuthEventType.TOKEN_CREATED,
              result = AuditEventResult.SUCCESS,
              ipAddress = "127.0.0.1",
              metadata = JsonObject(mapOf("source" to JsonPrimitive("test"))),
            )
          )

        event.authEventId.value.startsWith("aut_") shouldBe true
        event.ipAddress shouldBe "127.0.0.1"
        events.listRecentByUser(user.id, 5).shouldHaveSize(1)
        events.listRecentByLoginAccount(loginAccount.id, 5).single().ipAddress shouldBe "127.0.0.1"
      }
    }

    "session and bearer token repositories create touch and revoke active credentials" {
      withPostgresDatabase { database ->
        val users = ExposedUserRepository(database)
        val loginMethods = ExposedLoginMethodRepository(database)
        val loginAccounts = ExposedLoginAccountStore(database)
        val sessions = ExposedAuthSessionRepository(database)
        val tokens = ExposedBearerTokenRepository(database)
        val user = users.create(CreateUserCommand("Lin", "lin@example.test"))
        val method =
          loginMethods.createLoginMethod(
            CreateLoginMethodDefinitionCommand("api_token", LoginMethodKind.API_TOKEN, "API Token")
          )
        val loginAccount =
          loginAccounts.createLoginAccount(
            CreateLoginAccountCommand(method.id, "lin-token", "lin-token", "Lin token")
          )
        val expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1)

        val session =
          sessions.create(
            CreateAuthSessionCommand(
              sessionHash = "session-hash",
              userId = user.id,
              loginAccountId = loginAccount.id,
              expiresAt = expiresAt,
            )
          )
        val token =
          tokens.create(
            CreateBearerTokenCommand(
              tokenHash = "token-hash",
              userId = user.id,
              loginAccountId = loginAccount.id,
              expiresAt = expiresAt,
            )
          )

        sessions.findActiveByHash("session-hash", OffsetDateTime.now(ZoneOffset.UTC))?.id shouldBe
          session.id
        tokens.findActiveByHash("token-hash", OffsetDateTime.now(ZoneOffset.UTC))?.id shouldBe
          token.id

        sessions.touch(session.id, OffsetDateTime.now(ZoneOffset.UTC)) shouldBe true
        tokens.touch(token.id, OffsetDateTime.now(ZoneOffset.UTC)) shouldBe true
        sessions.revoke(session.id, OffsetDateTime.now(ZoneOffset.UTC)) shouldBe true
        tokens.revoke(token.id, OffsetDateTime.now(ZoneOffset.UTC)) shouldBe true
        sessions.findActiveByHash("session-hash", OffsetDateTime.now(ZoneOffset.UTC)).shouldBeNull()
        tokens.findActiveByHash("token-hash", OffsetDateTime.now(ZoneOffset.UTC)).shouldBeNull()
      }
    }
  })

private fun withPostgresDatabase(block: suspend (Database) -> Unit) {
  PostgreSQLContainer("postgres:18-alpine").use { postgres ->
    postgres.start()
    Flyway.configure()
      .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
      .locations("classpath:db/migration")
      .load()
      .migrate()
    val database =
      Database.connect(
        url = postgres.jdbcUrl,
        driver = "org.postgresql.Driver",
        user = postgres.username,
        password = postgres.password,
      )
    kotlinx.coroutines.runBlocking { block(database) }
  }
}

private fun seedTenant(database: Database): UUID {
  val tenantId = UUID.randomUUID()
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  transaction(database) {
    TenantsTable.insert {
      it[id] = tenantId.toKotlinUuid()
      it[apiId] = PublicId.new("ten").value
      it[name] = "Test Tenant"
      it[slug] = "test-${tenantId.toString().take(8)}"
      it[timezone] = "UTC"
      it[locale] = "en-US"
      it[createdAt] = now
      it[updatedAt] = now
    }
  }
  return tenantId
}
