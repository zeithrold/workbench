package ink.doa.workbench.security.permission

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.permission.CreatePermissionBindingCommand
import ink.doa.workbench.core.permission.CreatePermissionGroupCommand
import ink.doa.workbench.core.permission.CreatePermissionPolicyCommand
import ink.doa.workbench.core.permission.CreatePermissionPolicyRuleCommand
import ink.doa.workbench.core.permission.PermissionBindingRecord
import ink.doa.workbench.core.permission.PermissionBindingRepository
import ink.doa.workbench.core.permission.PermissionGroupRecord
import ink.doa.workbench.core.permission.PermissionGroupRepository
import ink.doa.workbench.core.permission.PermissionPolicyRecord
import ink.doa.workbench.core.permission.PermissionPolicyRepository
import ink.doa.workbench.core.permission.PermissionPrincipalType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

class PermissionBootstrapServiceTest :
  StringSpec({
    val groups = mockk<PermissionGroupRepository>()
    val policies = mockk<PermissionPolicyRepository>()
    val bindings = mockk<PermissionBindingRepository>()
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val service = PermissionBootstrapService(groups, policies, bindings, clock)

    "provisionTenantAdmin creates group policy and binding when missing" {
      val tenantId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val actorUserId = UUID.randomUUID()
      val group = sampleGroup(tenantId)
      val policy = samplePolicy(tenantId, "tenant-admin")
      val bindingCommand = slot<CreatePermissionBindingCommand>()

      coEvery { groups.findByCode(tenantId, "tenant-admin") } returns null
      coEvery { groups.create(any()) } returns group
      coEvery { groups.addMember(any()) } returns mockk()
      coEvery { policies.findByCode(tenantId, "tenant-admin") } returns null
      coEvery { policies.create(any()) } returns policy
      coEvery { policies.addRule(any()) } returns mockk()
      coEvery { policies.findByCode(tenantId, "project-admin") } returnsMany
        listOf(null, samplePolicy(tenantId, "project-admin"))
      coEvery { policies.findByCode(tenantId, "project-member") } returns
        samplePolicy(tenantId, "project-member")
      coEvery { policies.findByCode(tenantId, "project-viewer") } returns
        samplePolicy(tenantId, "project-viewer")
      coEvery { bindings.listByTenant(tenantId) } returns emptyList()
      coEvery { bindings.create(capture(bindingCommand)) } returns mockk()

      runBlocking { service.provisionTenantAdmin(tenantId, userId, actorUserId) }

      bindingCommand.captured.tenantId shouldBe tenantId
      bindingCommand.captured.principalGroupId shouldBe group.id
      bindingCommand.captured.policyId shouldBe policy.id
      coVerify(exactly = 1) { groups.addMember(any()) }
    }

    "provisionProjectCreator binds project-admin policy to user" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val policy = samplePolicy(tenantId, "project-admin")
      val bindingCommand = slot<CreatePermissionBindingCommand>()

      coEvery { policies.findByCode(tenantId, "project-admin") } returns policy
      coEvery { bindings.create(capture(bindingCommand)) } returns mockk()

      runBlocking {
        service.provisionProjectCreator(tenantId, projectId, userId, actorUserId = userId)
      }

      bindingCommand.captured.projectId shouldBe projectId
      bindingCommand.captured.principalUserId shouldBe userId
      bindingCommand.captured.policyId shouldBe policy.id
    }
  })

private fun sampleGroup(tenantId: UUID): PermissionGroupRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return PermissionGroupRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("pgr"),
    tenantId = tenantId,
    code = "tenant-admin",
    name = "Tenant Admin",
    description = null,
    builtin = true,
    createdAt = now,
    updatedAt = now,
  )
}

private fun samplePolicy(tenantId: UUID, code: String): PermissionPolicyRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return PermissionPolicyRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("pol"),
    tenantId = tenantId,
    code = code,
    name = code,
    description = null,
    builtin = true,
    createdAt = now,
    updatedAt = now,
  )
}
