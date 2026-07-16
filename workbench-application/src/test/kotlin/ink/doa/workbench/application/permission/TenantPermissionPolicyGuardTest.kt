package ink.doa.workbench.application.permission

import ink.doa.workbench.identity.permission.PermissionPolicyRecord
import ink.doa.workbench.identity.permission.PermissionPolicyRepository
import ink.doa.workbench.identity.permission.PermissionPolicyRuleRecord
import ink.doa.workbench.identity.permission.model.AuthorizationAction
import ink.doa.workbench.identity.permission.model.PermissionEffect
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking

class TenantPermissionPolicyGuardTest :
  StringSpec({
    val policies = mockk<PermissionPolicyRepository>()
    val guard = TenantPermissionPolicyGuard(policies)
    val tenantId = UUID.randomUUID()

    "requirePolicy returns tenant policy" {
      val policy = policy(tenantId)
      coEvery { policies.findByApiId(tenantId, policy.apiId.value) } returns policy
      coEvery { policies.listRules(policy.id) } returns
        listOf(rule(policy, "tenant.read", "tenant:*"))

      runBlocking { guard.requirePolicy(tenantId, policy.apiId.value) } shouldBe policy
    }

    "requirePolicy rejects Agile policy" {
      val policy = policy(tenantId)
      coEvery { policies.findByApiId(tenantId, policy.apiId.value) } returns policy
      coEvery { policies.listRules(policy.id) } returns
        listOf(rule(policy, "issue.view", "issue:*"))

      shouldThrow<InvalidRequestException> {
        runBlocking { guard.requirePolicy(tenantId, policy.apiId.value) }
      }
    }

    "contains returns false for policy outside tenant" {
      coEvery { policies.findById(tenantId, any()) } returns null

      runBlocking { guard.contains(tenantId, UUID.randomUUID()) } shouldBe false
    }
  })

private fun policy(tenantId: UUID): PermissionPolicyRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return PermissionPolicyRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("pol"),
    tenantId = tenantId,
    code = "tenant-reader",
    name = "Tenant reader",
    description = null,
    builtin = false,
    createdAt = now,
    updatedAt = now,
  )
}

private fun rule(policy: PermissionPolicyRecord, action: String, resource: String) =
  PermissionPolicyRuleRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("prr"),
    policyId = policy.id,
    action = AuthorizationAction(action),
    resourcePattern = resource,
    effect = PermissionEffect.ALLOW,
    conditionJson = null,
    createdAt = policy.createdAt,
  )
