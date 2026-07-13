package ink.doa.workbench.identity

import ink.doa.workbench.identity.auth.AuthSessionRepository
import ink.doa.workbench.identity.common.IdentityPublicIdResolver
import ink.doa.workbench.identity.common.summary.UserSummary
import ink.doa.workbench.identity.model.AuthSessionRecord
import ink.doa.workbench.identity.model.AuthenticatedPrincipal
import ink.doa.workbench.identity.model.TenantMemberRecord
import ink.doa.workbench.identity.model.TenantMemberStatus
import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.PermissionDeniedException
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.TenantDestroyingException
import ink.doa.workbench.kernel.common.errors.TenantNotSelectedException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.tenant.TenantRepository
import ink.doa.workbench.tenant.common.summary.TenantSummary
import ink.doa.workbench.tenant.model.TenantRecord
import ink.doa.workbench.tenant.model.TenantStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
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
    val publicIds = mockk<IdentityPublicIdResolver>()
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

    "switchTenant updates active tenant for active membership" {
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
          status = TenantMemberStatus.ACTIVE,
          joinedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
          invitedBy = null,
          createdAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
        )
      coEvery { sessions.updateActiveTenant(sessionId, tenant.id, any()) } returns true
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
      coEvery { loginCompletionService.adminScopes(user.id) } returns emptyList()

      val view = runBlocking { service.switchTenant(principal, tenant.apiId.value) }

      view.activeTenant shouldBe TenantSummary.from(tenant)
    }

    "getCurrent throws when session is missing" {
      val sessionId = UUID.randomUUID()
      val principal =
        AuthenticatedPrincipal(
          user = sampleUser(),
          loginAccountId = UUID.randomUUID(),
          sessionId = sessionId.toString(),
          bearerTokenId = null,
        )
      coEvery { sessions.findById(sessionId) } returns null

      shouldThrow<InvalidRequestException> {
          runBlocking { service.getCurrent(principal) }
        }
        .errorCode shouldBe WorkbenchErrorCode.SESSION_ACTIVE_NOT_FOUND
    }

    "getCurrent returns null active tenant when session has no tenant" {
      val user = sampleUser()
      val sessionId = UUID.randomUUID()
      val principal =
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = UUID.randomUUID(),
          sessionId = sessionId.toString(),
          bearerTokenId = null,
        )
      coEvery { sessions.findById(sessionId) } returns
        AuthSessionRecord(
          id = sessionId,
          sessionHash = "hash",
          userId = user.id,
          loginAccountId = UUID.randomUUID(),
          activeTenantId = null,
          expiresAt = OffsetDateTime.parse("2026-07-05T00:00:00Z"),
          revokedAt = null,
          lastUsedAt = null,
          createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )
      coEvery { loginCompletionService.adminScopes(user.id) } returns emptyList()

      val view = runBlocking { service.getCurrent(principal) }

      view.activeTenant.shouldBeNull()
    }

    "switchTenant clears active tenant when tenant id is null" {
      val user = sampleUser()
      val sessionId = UUID.randomUUID()
      val principal =
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = UUID.randomUUID(),
          sessionId = sessionId.toString(),
          bearerTokenId = null,
          tenantId = null,
        )
      coEvery {
        sessions.updateActiveTenant(sessionId, null, OffsetDateTime.parse("2026-07-04T00:00:00Z"))
      } returns true
      coEvery { sessions.findById(sessionId) } returns
        AuthSessionRecord(
          id = sessionId,
          sessionHash = "hash",
          userId = user.id,
          loginAccountId = UUID.randomUUID(),
          activeTenantId = null,
          expiresAt = OffsetDateTime.parse("2026-07-05T00:00:00Z"),
          revokedAt = null,
          lastUsedAt = null,
          createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )
      coEvery { loginCompletionService.adminScopes(user.id) } returns emptyList()

      val view = runBlocking { service.switchTenant(principal, tenantId = null) }

      view.activeTenant.shouldBeNull()
    }

    "switchTenant rejects destroying tenants" {
      val user = sampleUser()
      val tenant = sampleTenant().copy(status = TenantStatus.DESTROYING)
      val sessionId = UUID.randomUUID()
      val principal =
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = UUID.randomUUID(),
          sessionId = sessionId.toString(),
          bearerTokenId = null,
        )
      coEvery { publicIds.resolveTenant(tenant.apiId.value) } returns tenant
      coEvery { tenants.findById(tenant.id) } returns tenant

      shouldThrow<TenantDestroyingException> {
        runBlocking { service.switchTenant(principal, tenant.apiId.value) }
      }
    }

    "switchTenant rejects unknown tenants" {
      val user = sampleUser()
      val tenant = sampleTenant()
      val principal =
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = UUID.randomUUID(),
          sessionId = UUID.randomUUID().toString(),
          bearerTokenId = null,
        )
      coEvery { publicIds.resolveTenant(tenant.apiId.value) } returns tenant
      coEvery { tenants.findById(tenant.id) } returns null

      shouldThrow<ResourceNotFoundException> {
          runBlocking { service.switchTenant(principal, tenant.apiId.value) }
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_TENANT_NOT_FOUND
    }

    "switchTenant throws when session update fails" {
      val user = sampleUser()
      val tenant = sampleTenant()
      val sessionId = UUID.randomUUID()
      val principal =
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = UUID.randomUUID(),
          sessionId = sessionId.toString(),
          bearerTokenId = null,
        )
      coEvery { publicIds.resolveTenant(tenant.apiId.value) } returns tenant
      coEvery { tenants.findById(tenant.id) } returns tenant
      coEvery { tenantMembers.findByTenantAndUser(tenant.id, user.id) } returns
        TenantMemberRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("tmb"),
          tenantId = tenant.id,
          userId = user.id,
          status = TenantMemberStatus.ACTIVE,
          joinedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
          invitedBy = null,
          createdAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
        )
      coEvery { sessions.updateActiveTenant(sessionId, tenant.id, any()) } returns false

      shouldThrow<InvalidRequestException> {
          runBlocking { service.switchTenant(principal, tenant.apiId.value) }
        }
        .errorCode shouldBe WorkbenchErrorCode.SESSION_TENANT_UPDATE_FAILED
    }

    "requireActiveTenantId uses principal tenant when membership is active" {
      val user = sampleUser()
      val tenant = sampleTenant()
      val principal =
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = UUID.randomUUID(),
          sessionId = UUID.randomUUID().toString(),
          bearerTokenId = null,
          tenantId = tenant.id,
        )
      coEvery { tenantMembers.findByTenantAndUser(tenant.id, user.id) } returns
        TenantMemberRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("tmb"),
          tenantId = tenant.id,
          userId = user.id,
          status = TenantMemberStatus.ACTIVE,
          joinedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
          invitedBy = null,
          createdAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
        )
      coEvery { tenants.findById(tenant.id) } returns tenant

      runBlocking { service.requireActiveTenantId(principal) } shouldBe tenant.id
    }

    "requireActiveTenantId throws when session has no active tenant" {
      val user = sampleUser()
      val sessionId = UUID.randomUUID()
      val principal =
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = UUID.randomUUID(),
          sessionId = sessionId.toString(),
          bearerTokenId = null,
          tenantId = null,
        )
      coEvery { sessions.findById(sessionId) } returns
        AuthSessionRecord(
          id = sessionId,
          sessionHash = "hash",
          userId = user.id,
          loginAccountId = UUID.randomUUID(),
          activeTenantId = null,
          expiresAt = OffsetDateTime.parse("2026-07-05T00:00:00Z"),
          revokedAt = null,
          lastUsedAt = null,
          createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )

      shouldThrow<TenantNotSelectedException> {
        runBlocking { service.requireActiveTenantId(principal) }
      }
    }

    "requireActiveTenant returns tenant record for active membership" {
      val user = sampleUser()
      val tenant = sampleTenant()
      val principal =
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = UUID.randomUUID(),
          sessionId = UUID.randomUUID().toString(),
          bearerTokenId = null,
          tenantId = tenant.id,
        )
      coEvery { tenantMembers.findByTenantAndUser(tenant.id, user.id) } returns
        TenantMemberRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("tmb"),
          tenantId = tenant.id,
          userId = user.id,
          status = TenantMemberStatus.ACTIVE,
          joinedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
          invitedBy = null,
          createdAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
        )
      coEvery { tenants.findById(tenant.id) } returns tenant

      runBlocking { service.requireActiveTenant(principal) } shouldBe tenant
    }

    "tenantSummary returns summary when tenant exists" {
      val tenant = sampleTenant()
      coEvery { tenants.findById(tenant.id) } returns tenant

      runBlocking { service.tenantSummary(tenant.id) } shouldBe TenantSummary.from(tenant)
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
