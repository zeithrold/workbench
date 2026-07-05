package ink.doa.workbench.service.instance

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.model.CreateTenantCommand
import ink.doa.workbench.core.identity.model.CreateTenantWithAdminCommand
import ink.doa.workbench.core.identity.model.TenantAdminAssignment
import ink.doa.workbench.core.identity.model.TenantRecord
import ink.doa.workbench.core.identity.model.TenantStatus
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import ink.doa.workbench.security.identity.TenantLoginMethodService
import ink.doa.workbench.security.identity.UserLookupService
import ink.doa.workbench.security.invitation.InvitationService
import ink.doa.workbench.security.permission.AdminUserService
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

    fun service(publisher: DomainEventPublisher): TenantManagementApplicationService =
      TenantManagementApplicationService(
        dependencies =
          TenantManagementDependencies(
            tenants = TenantService(tenants),
            tenantLoginMethods = tenantLoginMethods,
            userLookupService = UserLookupService(users),
            adminUserService = adminUserService,
            invitationService = invitationService,
            clock = clock,
          ),
        domainEventPublisher = publisher,
      )

    beforeEach {
      coEvery { tenants.create(any()) } returns tenant
      coEvery { tenants.findByApiIdForAdmin("ten_01JABCDEFGHJKMNPQRSTVWXYZ0") } returns tenant
      coEvery { users.findById(actorId) } returns actor
      coEvery { tenants.markDestroying(tenantId) } returns
        tenant.copy(status = TenantStatus.DESTROYING)
    }

    "createWithAdmin self assignment requires authenticated actor" {
      val publisher = mockk<DomainEventPublisher>(relaxed = true)

      shouldThrow<InvalidRequestException> {
          runBlocking {
            service(publisher)
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
      val publisher = mockk<DomainEventPublisher>(relaxed = true)

      shouldThrow<InvalidRequestException> {
          runBlocking {
            service(publisher)
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

    "requestDestroy restores tenant status when event publish fails" {
      val publisher = mockk<DomainEventPublisher>()
      coEvery { publisher.publish(any(), any(), any(), any()) } throws
        RuntimeException("broker down")
      coEvery { tenants.update(any()) } returns tenant

      shouldThrow<RuntimeException> {
        runBlocking {
          service(publisher)
            .requestDestroy(
              tenantPublicId = "ten_01JABCDEFGHJKMNPQRSTVWXYZ0",
              actorUserId = actorId,
              deleteReason = "cleanup",
            )
        }
      }

      coVerify(exactly = 1) {
        tenants.update(match { it.tenantId == tenantId && it.status == TenantStatus.ACTIVE })
      }
    }

    "create provisions password login and returns tenant" {
      val publisher = mockk<DomainEventPublisher>(relaxed = true)

      val created = runBlocking {
        service(publisher)
          .create(
            CreateTenantCommand(name = "Acme", slug = "acme", timezone = "UTC", locale = "en-US")
          )
      }

      created.slug shouldBe "acme"
      coVerify(atLeast = 1) { tenantLoginMethods.enablePasswordLoginMethod(tenantId) }
    }
  })
