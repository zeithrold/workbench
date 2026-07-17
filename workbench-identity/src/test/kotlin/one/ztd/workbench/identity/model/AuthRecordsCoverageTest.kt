package one.ztd.workbench.identity.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.kernel.common.ids.PublicId

class AuthRecordsCoverageTest :
  StringSpec({
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val tenantId = UUID.randomUUID()
    val userId = UUID.randomUUID()

    "auth persistence records expose linkage metadata" {
      val loginMethodId = UUID.randomUUID()
      TenantMemberRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("tmb"),
          tenantId = tenantId,
          userId = userId,
          status = TenantMemberStatus.ACTIVE,
          joinedAt = now,
          invitedBy = null,
          createdAt = now,
          updatedAt = now,
        )
        .status shouldBe TenantMemberStatus.ACTIVE

      LoginMethodDefinitionRecord(
          id = loginMethodId,
          apiId = PublicId.new("lmd"),
          code = "password",
          kind = LoginMethodKind.PASSWORD,
          name = "Password",
          isBuiltin = true,
          isEnabledGlobally = true,
          createdAt = now,
          updatedAt = now,
        )
        .code shouldBe "password"

      TenantLoginMethodSettingRecord(
          id = UUID.randomUUID(),
          tenantId = tenantId,
          loginMethodId = loginMethodId,
          isEnabled = true,
          allowSignup = false,
          displayOrder = 1,
          secretRef = null,
          createdBy = null,
          updatedBy = null,
          createdAt = now,
          updatedAt = now,
        )
        .isEnabled shouldBe true

      val loginAccountId = UUID.randomUUID()
      LoginAccountRecord(
          id = loginAccountId,
          apiId = PublicId.new("lac"),
          loginMethodId = loginMethodId,
          subject = "ada@example.test",
          normalizedSubject = "ada@example.test",
          displayName = "Ada",
          lastUsedAt = null,
          disabledAt = null,
          disabledBy = null,
          createdAt = now,
          updatedAt = now,
        )
        .normalizedSubject shouldBe "ada@example.test"

      LoginAccountParameterRecord(
          id = UUID.randomUUID(),
          loginAccountId = loginAccountId,
          parameterKey = LoginAccountParameterKey.PasswordHash,
          parameterValue = "hash",
          secretRef = null,
          createdAt = now,
          updatedAt = now,
        )
        .parameterKey shouldBe LoginAccountParameterKey.PasswordHash
    }

    "session and token records expose credential metadata" {
      AuthEventRecord(
          id = UUID.randomUUID(),
          authEventId = PublicId.new("aev"),
          tenantId = tenantId,
          userId = userId,
          loginAccountId = UUID.randomUUID(),
          loginMethodId = UUID.randomUUID(),
          eventType = AuthEventType.LOGIN_SUCCESS,
          result = AuditEventResult.SUCCESS,
          failureReason = null,
          ipAddress = "127.0.0.1",
          userAgent = "test",
          occurredAt = now,
        )
        .eventType shouldBe AuthEventType.LOGIN_SUCCESS

      AuthSessionRecord(
          id = UUID.randomUUID(),
          sessionHash = "hash",
          userId = userId,
          loginAccountId = UUID.randomUUID(),
          activeTenantId = tenantId,
          expiresAt = now.plusHours(1),
          revokedAt = null,
          lastUsedAt = now,
          createdAt = now,
          updatedAt = now,
        )
        .sessionHash shouldBe "hash"

      BearerTokenRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("btk"),
          tokenHash = "hash",
          userId = userId,
          loginAccountId = UUID.randomUUID(),
          tenantId = tenantId,
          name = "ci-token",
          scopes = setOf("api"),
          createdBy = userId,
          expiresAt = now.plusDays(30),
          revokedAt = null,
          lastUsedAt = null,
          createdAt = now,
          updatedAt = now,
        )
        .name shouldBe "ci-token"

      IssuedCredential(
          id = UUID.randomUUID(),
          apiId = PublicId.new("iss"),
          secret = "secret",
          expiresAt = now.plusHours(1),
        )
        .secret shouldBe "secret"
    }

    "auth command records carry login and session metadata" {
      LoginCommand(
          method = LoginMethodKind.PASSWORD,
          tenantId = tenantId.toString(),
          subject = "ada@example.test",
          password = "password",
          ipAddress = "127.0.0.1",
          userAgent = "test",
        )
        .method shouldBe LoginMethodKind.PASSWORD

      CreateAuthSessionCommand(
          sessionHash = "hash",
          userId = userId,
          loginAccountId = UUID.randomUUID(),
          expiresAt = now.plusHours(1),
          activeTenantId = tenantId,
        )
        .sessionHash shouldBe "hash"

      CreateBearerTokenCommand(
          tokenHash = "hash",
          userId = userId,
          loginAccountId = UUID.randomUUID(),
          expiresAt = now.plusDays(30),
          tenantId = tenantId,
          name = "api",
        )
        .name shouldBe "api"
    }

    "federated and magic link records expose state" {
      AuthLoginStateRecord(
          id = UUID.randomUUID(),
          stateHash = "state",
          tenantId = tenantId,
          loginMethodId = UUID.randomUUID(),
          redirectUri = "http://localhost/callback",
          pkceVerifier = "verifier",
          returnUrl = "/home",
          expiresAt = now.plusMinutes(10),
          consumedAt = null,
          createdAt = now,
        )
        .stateHash shouldBe "state"

      MagicLinkTokenRecord(
          id = UUID.randomUUID(),
          tokenHash = "hash",
          loginMethodId = UUID.randomUUID(),
          tenantId = tenantId,
          normalizedSubject = "ada@example.test",
          expiresAt = now.plusMinutes(15),
          consumedAt = null,
          createdAt = now,
        )
        .tokenHash shouldBe "hash"

      CreateAuthEventCommand(
          tenantId = tenantId,
          userId = userId,
          eventType = AuthEventType.LOGIN_SUCCESS,
          result = AuditEventResult.SUCCESS,
        )
        .eventType shouldBe AuthEventType.LOGIN_SUCCESS

      UpsertLoginAccountParameterCommand(
          loginAccountId = UUID.randomUUID(),
          parameterKey = LoginAccountParameterKey.ApiTokenHash,
          parameterValue = "hash",
        )
        .parameterKey shouldBe LoginAccountParameterKey.ApiTokenHash
    }
  })
