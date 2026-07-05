package ink.doa.workbench.security.permission

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.core.permission.AccessGrantRecord
import ink.doa.workbench.core.permission.AccessGrantRepository
import ink.doa.workbench.core.permission.GrantScope
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.security.common.PublicIdResolver
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
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

class AccessGrantManagementServiceTest :
  StringSpec({
    val accessGrants = mockk<AccessGrantRepository>()
    val publicIds = mockk<PublicIdResolver>()
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val service = AccessGrantManagementService(accessGrants, publicIds, clock)
    val tenantId = UUID.randomUUID()
    val userId = UUID.randomUUID()

    "listGrants returns instance grants when scope is INSTANCE" {
      val grant = sampleGrant(GrantScope.INSTANCE, tenantId = null, subjectUserId = userId)
      coEvery { accessGrants.listInstanceGrants() } returns listOf(grant)

      val result = runBlocking { service.listGrants(GrantScope.INSTANCE, null, null) }

      result.single().id shouldBe grant.apiId.value
    }

    "listGrants returns tenant grants when tenantId is provided" {
      val grant = sampleGrant(GrantScope.TENANT, tenantId = tenantId, subjectUserId = userId)
      coEvery { accessGrants.listByTenant(tenantId) } returns listOf(grant)

      val result = runBlocking { service.listGrants(null, tenantId, null) }

      result.single().tenantId shouldBe tenantId.toString()
    }

    "listGrants returns subject grants when subjectUserId is provided" {
      val grant = sampleGrant(GrantScope.TENANT, tenantId = tenantId, subjectUserId = userId)
      coEvery { accessGrants.listBySubject(userId, null, null, null) } returns listOf(grant)

      val result = runBlocking { service.listGrants(null, null, userId) }

      result.single().userId shouldBe userId.toString()
    }

    "listGrants returns empty list when no filters are provided" {
      runBlocking { service.listGrants(null, null, null) }.shouldBeEmpty()
    }

    "createGrant creates grant for resolved user" {
      val user = sampleUser(userId)
      val grant = sampleGrant(GrantScope.TENANT, tenantId = tenantId, subjectUserId = userId)
      coEvery { publicIds.resolveUser(user.apiId.value) } returns user
      coEvery { accessGrants.create(any()) } returns grant

      val result = runBlocking {
        service.createGrant(
          CreateManagedAccessGrantCommand(
            scope = GrantScope.TENANT,
            tenantId = tenantId,
            userPublicId = user.apiId.value,
            actionCode = "tenant.read",
            resourcePattern = "tenant:*",
            effect = PermissionEffect.ALLOW,
            projectPublicId = null,
            actorUserId = UUID.randomUUID(),
          )
        )
      }

      result.action shouldBe "tenant.read"
      result.userId shouldBe userId.toString()
    }

    "expireGrant returns false when grant is missing" {
      coEvery { accessGrants.findByApiId("agr_missing") } returns null

      runBlocking { service.expireGrant("agr_missing") } shouldBe false
    }

    "expireGrant expires existing grant" {
      val grant = sampleGrant(GrantScope.INSTANCE, tenantId = null, subjectUserId = userId)
      coEvery { accessGrants.findByApiId(grant.apiId.value) } returns grant
      coEvery { accessGrants.expire(grant.id, any()) } returns true

      runBlocking { service.expireGrant(grant.apiId.value) } shouldBe true
      coVerify { accessGrants.expire(grant.id, OffsetDateTime.parse("2026-07-04T00:00:00Z")) }
    }
  })

private fun sampleUser(id: UUID): UserRecord =
  UserRecord(
    id = id,
    apiId = PublicId.new("usr"),
    displayName = "Ada",
    primaryEmail = "ada@example.test",
  )

private fun sampleGrant(
  scope: GrantScope,
  tenantId: UUID?,
  subjectUserId: UUID,
): AccessGrantRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return AccessGrantRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("agr"),
    scope = scope,
    tenantId = tenantId,
    projectId = null,
    subjectUserId = subjectUserId,
    action = AuthorizationAction("tenant.read"),
    resourcePattern = "tenant:*",
    effect = PermissionEffect.ALLOW,
    validFrom = now,
    validTo = null,
    grantedBy = null,
    createdAt = now,
  )
}
