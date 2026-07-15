package ink.doa.workbench.data.repository.identity

import ink.doa.workbench.data.persistence.postgres.identity.LoginAccountsTable
import ink.doa.workbench.data.persistence.postgres.identity.TenantsTable
import ink.doa.workbench.data.support.withCorePostgresDatabase
import ink.doa.workbench.identity.model.AuditEventResult
import ink.doa.workbench.identity.model.AuthEventType
import ink.doa.workbench.identity.model.CreateAuthEventCommand
import ink.doa.workbench.identity.model.CreateAuthSessionCommand
import ink.doa.workbench.identity.model.CreateBearerTokenCommand
import ink.doa.workbench.identity.model.CreateLoginAccountCommand
import ink.doa.workbench.identity.model.CreateLoginMethodDefinitionCommand
import ink.doa.workbench.identity.model.CreateTenantLoginMethodSettingCommand
import ink.doa.workbench.identity.model.CreateTenantMemberCommand
import ink.doa.workbench.identity.model.CreateUserCommand
import ink.doa.workbench.identity.model.LinkUserLoginAccountCommand
import ink.doa.workbench.identity.model.LoginAccountParameterKey
import ink.doa.workbench.identity.model.LoginMethodKind
import ink.doa.workbench.identity.model.TenantMemberStatus
import ink.doa.workbench.identity.model.UpsertLoginAccountParameterCommand
import ink.doa.workbench.kernel.common.ids.PublicId
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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class ExposedIdentityRepositoriesIntegrationTest :
  StringSpec({
    "user locale preference can be set and cleared" {
      withCorePostgresDatabase { database ->
        val users = ExposedUserRepository(database)
        val user = users.create(CreateUserCommand("Ada", "ada@example.test"))
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        users.updateLocale(user.id, "en-US", now)?.locale shouldBe "en-US"
        users.findById(user.id)?.locale shouldBe "en-US"
        users.updateLocale(user.id, null, now.plusSeconds(1))?.locale.shouldBeNull()
        users.findById(user.id)?.locale.shouldBeNull()
      }
    }

    "login account binding can resolve a user and stops resolving after unlink or disable" {
      withCorePostgresDatabase { database ->
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
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val member =
          members.create(
            CreateTenantMemberCommand(
              tenantId = tenantId,
              userId = user.id,
              status = TenantMemberStatus.ACTIVE,
              joinedAt = OffsetDateTime.now(ZoneOffset.UTC),
            )
          )
        members.findByApiId(tenantId, member.apiId.value)?.id shouldBe member.id
        members.listByTenant(tenantId).map { it.id } shouldBe listOf(member.id)
        members.updateStatus(member.id, TenantMemberStatus.SUSPENDED, now)?.status shouldBe
          TenantMemberStatus.SUSPENDED
        members.updateStatus(member.id, TenantMemberStatus.ACTIVE, now)?.status shouldBe
          TenantMemberStatus.ACTIVE
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
        loginAccounts
          .findLoginAccountByParameterValue(
            loginMethodCode = "password",
            parameterKey = LoginAccountParameterKey.PasswordHash,
            parameterValue = "hash:v1",
          )
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
      withCorePostgresDatabase { database ->
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
      withCorePostgresDatabase { database ->
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
