package ink.doa.workbench.security.permission

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.permission.PermissionPolicyRecord
import ink.doa.workbench.core.permission.PermissionPolicyRepository
import ink.doa.workbench.core.permission.UpdatePermissionPolicyCommand
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

class PermissionPolicyManagementServiceTest :
  StringSpec({
    val policies = mockk<PermissionPolicyRepository>()
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val service = PermissionPolicyManagementService(policies, clock)
    val tenantId = UUID.randomUUID()

    "createPolicy rejects duplicate code" {
      coEvery { policies.findByCode(tenantId, "custom") } returns samplePolicy(tenantId, "custom")

      shouldThrow<InvalidRequestException> {
        runBlocking { service.createPolicy(tenantId, "custom", "Custom", null) }
      }
    }

    "createPolicy returns view for new policy" {
      val policy = samplePolicy(tenantId, "custom", builtin = false)
      coEvery { policies.findByCode(tenantId, "custom") } returns null
      coEvery { policies.create(any()) } returns policy

      val result = runBlocking { service.createPolicy(tenantId, "custom", "Custom", null) }

      result.code shouldBe "custom"
      result.rules shouldBe emptyList()
    }

    "updatePolicy rejects builtin policy changes" {
      val policy = samplePolicy(tenantId, "tenant-admin", builtin = true)
      coEvery { policies.findByApiId(tenantId, policy.apiId.value) } returns policy

      shouldThrow<InvalidRequestException> {
        runBlocking { service.updatePolicy(tenantId, policy.apiId.value, "Renamed", null) }
      }
    }

    "updatePolicy delegates to repository for custom policy" {
      val policy = samplePolicy(tenantId, "custom", builtin = false)
      val updated = policy.copy(name = "Renamed")
      coEvery { policies.findByApiId(tenantId, policy.apiId.value) } returns policy
      coEvery { policies.update(UpdatePermissionPolicyCommand(policy.id, "Renamed", null)) } returns
        updated
      coEvery { policies.listRules(policy.id) } returns emptyList()

      val result = runBlocking {
        service.updatePolicy(tenantId, policy.apiId.value, "Renamed", null)
      }

      result.name shouldBe "Renamed"
    }
  })

private fun samplePolicy(
  tenantId: UUID,
  code: String,
  builtin: Boolean = true,
): PermissionPolicyRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return PermissionPolicyRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("pol"),
    tenantId = tenantId,
    code = code,
    name = code,
    description = null,
    builtin = builtin,
    createdAt = now,
    updatedAt = now,
  )
}
