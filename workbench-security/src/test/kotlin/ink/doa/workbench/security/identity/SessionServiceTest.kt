package ink.doa.workbench.security.identity

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.PermissionDeniedException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.common.summary.TenantSummary
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.identity.TenantMemberRepository
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.auth.AuthSessionRepository
import ink.doa.workbench.core.identity.model.AuthSessionRecord
import ink.doa.workbench.core.identity.model.AuthenticatedPrincipal
import ink.doa.workbench.core.identity.model.TenantMemberRecord
import ink.doa.workbench.core.identity.model.TenantMemberStatus
import ink.doa.workbench.core.identity.model.TenantRecord
import ink.doa.workbench.core.identity.model.TenantStatus
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.security.common.PublicIdResolver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

class SessionServiceTest :
  StringSpec({
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val sessions = mockk<AuthSessionRepository>()
    val tenantMembers = mockk<TenantMemberRepository>()
    val tenants = mockk<TenantRepository>()
    val publicIds = mockk<PublicIdResolver>()
    val loginCompletionService = mockk<LoginCompletionService>()
    val service =
      SessionService(
        sessions,
        tenantMembers,
        tenants,
        publicIds,
        loginCompletionService,
        clock,
      )

    "getCurrent returns session view with active tenant" {
      val user = sampleUser()
      val tenant = sampleTenant()
      val sessionId = UUID.randomUUID()
      val principal =
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = UUID.randomUUID(),
          sessionId = sessionId.toString(),
          bearerTokenId = null,
          tenantId = tenant.id,
        )
      coEvery { sessions.findById(sessionId) } returns
        AuthSessionRecord(
          id = sessionId,
          sessionHash = "hash",
          userId = user.id,
          loginAccountId = UUID.randomUUID(),
          activeTenantId = tenant.id,
          expiresAt = OffsetDateTime.parse("2026-07-05T00:00:00Z"),
          revokedAt = null,
          lastUsedAt = null,
          createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )
      coEvery { tenants.findById(tenant.id) } returns tenant
      coEvery { loginCompletionService.adminScopes(user.id) } returns emptyList()

      val view = runBlocking { service.getCurrent(principal) }

      view.user shouldBe UserSummary.from(user)
      view.activeTenant shouldBe TenantSummary.from(tenant)
    }

    "switchTenant rejects inactive membership" {
      val user = sampleUser()
      val tenant = sampleTenant()
      val sessionId = UUID.randomUUID()
      val principal =
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = UUID.randomUUID(),
          sessionId = sessionId.toString(),
          bearerTokenId = null,
          tenantId = null,
        )
      coEvery { publicIds.resolveTenant(tenant.apiId.value) } returns tenant
      coEvery { tenants.findById(tenant.id) } returns tenant
      coEvery { tenantMembers.findByTenantAndUser(tenant.id, user.id) } returns
        TenantMemberRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("tmb"),
          tenantId = tenant.id,
          userId = user.id,
          status = TenantMemberStatus.SUSPENDED,
          joinedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
          invitedBy = null,
          createdAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
        )

      shouldThrow<PermissionDeniedException> {
        runBlocking { service.switchTenant(principal, tenant.apiId.value) }
      }
        .errorCode shouldBe WorkbenchErrorCode.AUTH_TENANT_MEMBERSHIP_REQUIRED
    }
  })

private fun sampleUser(): UserRecord =
  UserRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("usr"),
    displayName = "Ada",
    primaryEmail = "ada@example.test",
  )

private fun sampleTenant(): TenantRecord =
  TenantRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("ten"),
    slug = "acme",
    name = "Acme",
    status = TenantStatus.ACTIVE,
    createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
  )
