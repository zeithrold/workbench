package ink.doa.workbench.application.instance

import ink.doa.workbench.application.invitation.InvitationService
import ink.doa.workbench.application.permission.AdminUserService
import ink.doa.workbench.identity.TenantLoginMethodService
import ink.doa.workbench.identity.UserLookupService
import ink.doa.workbench.identity.UserRepository
import ink.doa.workbench.identity.model.CreateTenantWithAdminCommand
import ink.doa.workbench.identity.model.TenantAdminAssignment
import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.tenant.TenantRepository
import ink.doa.workbench.tenant.model.CreateTenantCommand
import ink.doa.workbench.tenant.model.TenantRecord
import ink.doa.workbench.tenant.model.TenantStatus
import ink.doa.workbench.tenant.tenant.TenantService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

class TenantManagementApplicationServiceTest :
  StringSpec({
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val tenants = mockk<TenantRepository>()
    val users = mockk<UserRepository>()
    val tenantLoginMethods = mockk<TenantLoginMethodService>(relaxed = true)
    val adminUserService = mockk<AdminUserService>(relaxed = true)
    val invitationService = mockk<InvitationService>(relaxed = true)

    val tenantId = UUID.randomUUID()
    val actorId = UUID.randomUUID()
    val tenant =
      TenantRecord(
        id = tenantId,
        apiId = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        slug = "acme",
        name = "Acme",
        status = TenantStatus.ACTIVE,
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )
    val actor =
      UserRecord(
        id = actorId,
        apiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
        displayName = "Admin",
        primaryEmail = "admin@example.test",
      )

    fun service(): TenantManagementApplicationService =
      TenantManagementApplicationService(
        dependencies =
          TenantManagementDependencies(
            tenants = TenantService(tenants),
            identity =
              TenantIdentityDependencies(
                tenantLoginMethods = tenantLoginMethods,
                userLookupService = UserLookupService(users),
                adminUserService = adminUserService,
                invitationService = invitationService,
              ),
            defaultWorkItemTemplate = mockk(relaxed = true),
            clock = clock,
          )
      )

    beforeEach {
      coEvery { tenants.create(any()) } returns tenant
      coEvery { tenants.findByApiIdForAdmin("ten_01JABCDEFGHJKMNPQRSTVWXYZ0") } returns tenant
      coEvery { users.findById(actorId) } returns actor
    }

    "createWithAdmin self assignment requires authenticated actor" {
      shouldThrow<InvalidRequestException> {
          runBlocking {
            service()
              .createWithAdmin(
                command =
                  CreateTenantWithAdminCommand(
                    name = "Acme",
                    slug = "acme",
                    adminAssignment = TenantAdminAssignment.SelfAssignment,
                  ),
                actorUserId = null,
                requestHost = null,
              )
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED
    }

    "createWithAdmin email invite requires authenticated actor" {
      shouldThrow<InvalidRequestException> {
          runBlocking {
            service()
              .createWithAdmin(
                command =
                  CreateTenantWithAdminCommand(
                    name = "Acme",
                    slug = "acme",
                    adminAssignment =
                      TenantAdminAssignment.EmailInviteAssignment(
                        email = "admin@example.test",
                        displayName = "Admin",
                      ),
                  ),
                actorUserId = null,
                requestHost = null,
              )
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED
    }

    "create provisions password login and returns tenant" {
      val created = runBlocking {
        service()
          .create(
            CreateTenantCommand(name = "Acme", slug = "acme", timezone = "UTC", locale = "en-US")
          )
      }

      created.slug shouldBe "acme"
      coVerify(atLeast = 1) { tenantLoginMethods.enablePasswordLoginMethod(tenantId) }
    }
  })
