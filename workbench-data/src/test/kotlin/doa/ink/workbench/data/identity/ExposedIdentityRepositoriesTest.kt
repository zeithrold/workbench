package doa.ink.workbench.data.identity

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.identity.model.AuditEventResult
import doa.ink.workbench.core.identity.model.AuthEventType
import doa.ink.workbench.core.identity.model.CreateAuthEventCommand
import doa.ink.workbench.core.identity.model.CreateAuthSessionCommand
import doa.ink.workbench.core.identity.model.CreateBearerTokenCommand
import doa.ink.workbench.core.identity.model.CreateLoginAccountCommand
import doa.ink.workbench.core.identity.model.CreateLoginMethodDefinitionCommand
import doa.ink.workbench.core.identity.model.CreateTenantLoginMethodSettingCommand
import doa.ink.workbench.core.identity.model.CreateTenantMemberCommand
import doa.ink.workbench.core.identity.model.CreateUserCommand
import doa.ink.workbench.core.identity.model.LinkUserLoginAccountCommand
import doa.ink.workbench.core.identity.model.LoginAccountParameterKey
import doa.ink.workbench.core.identity.model.LoginMethodKind
import doa.ink.workbench.core.identity.model.TenantMemberStatus
import doa.ink.workbench.core.identity.model.UpsertLoginAccountParameterCommand
import doa.ink.workbench.data.persistence.LoginAccountsTable
import doa.ink.workbench.data.persistence.TenantsTable
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
        val accounts = ExposedLoginAccountRepository(database)

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
        val method = accounts.findLoginMethodByCode("password").shouldNotBeNull()
        accounts.createTenantSetting(
          CreateTenantLoginMethodSettingCommand(tenantId = tenantId, loginMethodId = method.id)
        )
        val loginAccount =
          accounts.createLoginAccount(
            CreateLoginAccountCommand(
              loginMethodId = method.id,
              subject = "Ada@Example.Test",
              normalizedSubject = "ada@example.test",
              displayName = "Ada",
            )
          )
        val parameter =
          accounts.upsertParameter(
            UpsertLoginAccountParameterCommand(
              loginAccountId = loginAccount.id,
              parameterKey = LoginAccountParameterKey.PasswordHash,
              parameterValue = "hash:v1",
              metadata = JsonObject(mapOf("algorithm" to JsonPrimitive("argon2id"))),
            )
          )
        accounts.linkUser(LinkUserLoginAccountCommand(user.id, loginAccount.id, linkedBy = user.id))

        member.userId shouldBe user.id
        parameter.parameterKey shouldBe LoginAccountParameterKey.PasswordHash
        accounts.findLoginAccountByMethodAndSubject("password", "ada@example.test")?.id shouldBe
          loginAccount.id
        accounts.findUserByMethodAndSubject("password", "ada@example.test")?.id shouldBe user.id

        accounts.unlink(loginAccount.id, OffsetDateTime.now(ZoneOffset.UTC)) shouldBe true
        accounts.findUserByMethodAndSubject("password", "ada@example.test").shouldBeNull()

        accounts.linkUser(LinkUserLoginAccountCommand(user.id, loginAccount.id, linkedBy = user.id))
        transaction(database) {
          LoginAccountsTable.update({ LoginAccountsTable.id eq loginAccount.id.toKotlinUuid() }) {
            it[disabledAt] = OffsetDateTime.now(ZoneOffset.UTC)
          }
        }
        accounts.findLoginAccountByMethodAndSubject("password", "ada@example.test").shouldBeNull()
      }
    }

    "auth events append and read back by user and login account" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val users = ExposedUserRepository(database)
        val accounts = ExposedLoginAccountRepository(database)
        val events = ExposedAuthEventRepository(database)
        val user = users.create(CreateUserCommand("Grace", "grace@example.test"))
        val method =
          accounts.createLoginMethod(
            CreateLoginMethodDefinitionCommand("api_token", LoginMethodKind.API_TOKEN, "API Token")
          )
        val loginAccount =
          accounts.createLoginAccount(
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
        val accounts = ExposedLoginAccountRepository(database)
        val sessions = ExposedAuthSessionRepository(database)
        val tokens = ExposedBearerTokenRepository(database)
        val user = users.create(CreateUserCommand("Lin", "lin@example.test"))
        val method =
          accounts.createLoginMethod(
            CreateLoginMethodDefinitionCommand("api_token", LoginMethodKind.API_TOKEN, "API Token")
          )
        val loginAccount =
          accounts.createLoginAccount(
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
