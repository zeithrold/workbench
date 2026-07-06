package ink.doa.workbench.web.instance

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.common.summary.LoginMethodSummary
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.identity.model.LoginMethodKind
import ink.doa.workbench.core.identity.model.TenantAdminAssignment
import ink.doa.workbench.core.identity.model.TenantRecord
import ink.doa.workbench.core.permission.AdminScope
import ink.doa.workbench.security.identity.LoginView
import ink.doa.workbench.security.permission.AdminUserView
import ink.doa.workbench.service.instance.CreateTenantView
import ink.doa.workbench.service.instance.InstanceBootstrapView
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking

class InstanceRequestsTest :
  StringSpec({
    "instance setup request maps to bootstrap command" {
      InstanceSetupRequest(
          displayName = "Admin",
          email = "admin@example.test",
          password = "secure-password-1",
          setupToken = "token",
        )
        .toCommand(ipAddress = "127.0.0.1", userAgent = "test-agent")
        .let { command ->
          command.displayName shouldBe "Admin"
          command.email shouldBe "admin@example.test"
          command.password shouldBe "secure-password-1"
          command.setupToken shouldBe "token"
          command.ipAddress shouldBe "127.0.0.1"
          command.userAgent shouldBe "test-agent"
        }
    }

    "create tenant request maps self assignment to command" {
      runBlocking {
        CreateTenantRequest(
            name = "Acme",
            slug = "acme",
            timezone = "UTC",
            locale = "en-US",
            adminAssignment = TenantAdminAssignmentRequest(mode = TenantAdminAssignmentMode.SELF),
          )
          .toCommand { UUID.fromString("00000000-0000-0000-0000-000000000099") }
          .let { command ->
            command.name shouldBe "Acme"
            command.slug shouldBe "acme"
            command.adminAssignment shouldBe TenantAdminAssignment.SelfAssignment
          }
      }
    }

    "create tenant request maps user assignment to command" {
      runBlocking {
        val userId = UUID.fromString("00000000-0000-0000-0000-000000000099")
        CreateTenantRequest(
            name = "Acme",
            slug = "acme",
            adminAssignment =
              TenantAdminAssignmentRequest(
                mode = TenantAdminAssignmentMode.USER,
                userId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
              ),
          )
          .toCommand { userId }
          .adminAssignment shouldBe TenantAdminAssignment.UserAssignment(userId)
      }
    }

    "create tenant request maps email invite assignment to command" {
      runBlocking {
        CreateTenantRequest(
            name = "Acme",
            slug = "acme",
            adminAssignment =
              TenantAdminAssignmentRequest(
                mode = TenantAdminAssignmentMode.EMAIL_INVITE,
                email = "admin@example.test",
                displayName = "Tenant Admin",
              ),
          )
          .toCommand { UUID.randomUUID() }
          .adminAssignment shouldBe
          TenantAdminAssignment.EmailInviteAssignment(
            email = "admin@example.test",
            displayName = "Tenant Admin",
          )
      }
    }

    "user assignment requires user id" {
      shouldThrow<InvalidRequestException> {
          runBlocking {
            TenantAdminAssignmentRequest(mode = TenantAdminAssignmentMode.USER, userId = null)
              .toAssignment { UUID.randomUUID() }
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.INSTANCE_SETUP_USER_ID_REQUIRED
    }

    "email invite assignment requires email" {
      shouldThrow<InvalidRequestException> {
          runBlocking {
            TenantAdminAssignmentRequest(
                mode = TenantAdminAssignmentMode.EMAIL_INVITE,
                email = null,
              )
              .toAssignment { UUID.randomUUID() }
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.INSTANCE_SETUP_EMAIL_REQUIRED
    }

    "tenant response maps record fields" {
      val record =
        TenantRecord(
          id = UUID.fromString("00000000-0000-0000-0000-000000000010"),
          apiId = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          slug = "acme",
          name = "Acme",
          timezone = "UTC",
          locale = "en-US",
          createdAt = OffsetDateTime.parse("2026-07-03T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-03T00:00:00Z"),
        )

      TenantResponse.from(record).let { response ->
        response.id shouldBe "ten_01JABCDEFGHJKMNPQRSTVWXYZ0"
        response.slug shouldBe "acme"
        response.status shouldBe "ACTIVE"
        response.admin shouldBe null
      }
    }

    "tenant response maps create view fields" {
      val tenant =
        TenantRecord(
          id = UUID.fromString("00000000-0000-0000-0000-000000000010"),
          apiId = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          slug = "acme",
          name = "Acme",
        )
      val view =
        CreateTenantView(
          tenant = tenant,
          admin =
            AdminUserView(
              id = "adm_abc",
              userId = "usr_abc",
              scope = AdminScope.TENANT,
              tenantId = tenant.apiId.value,
              status = "active",
              validFrom = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
              validTo = null,
            ),
          invitationLink = "https://example.test/invite",
        )

      TenantResponse.from(view).let { response ->
        response.admin?.id shouldBe "adm_abc"
        response.invitationLink shouldBe "https://example.test/invite"
      }
    }

    "instance bootstrap response maps view fields" {
      val view =
        InstanceBootstrapView(
          user =
            UserSummary(
              id = PublicId.new("usr"),
              displayName = "Admin",
              primaryEmail = "admin@example.test",
            ),
          loginMethod =
            LoginMethodSummary(
              id = "lmg_abc",
              code = "password",
              kind = LoginMethodKind.PASSWORD,
              name = "Password",
            ),
          session =
            LoginView(
              user =
                UserSummary(
                  id = PublicId.new("usr"),
                  displayName = "Admin",
                  primaryEmail = "admin@example.test",
                ),
              sessionExpiresAt = OffsetDateTime.parse("2026-07-04T01:00:00Z"),
              sessionSecret = "session-secret",
              bearerToken = null,
            ),
        )

      InstanceBootstrapResponse.from(view).session.sessionExpiresAt shouldBe
        OffsetDateTime.parse("2026-07-04T01:00:00Z")
    }

    "patch tenant request maps to update command" {
      val tenantId = UUID.fromString("00000000-0000-0000-0000-000000000010")
      PatchTenantRequest(name = "Acme Corp", slug = "acme-corp").toCommand(tenantId).let { command
        ->
        command.tenantId shouldBe tenantId
        command.name shouldBe "Acme Corp"
        command.slug shouldBe "acme-corp"
      }
    }
  })
