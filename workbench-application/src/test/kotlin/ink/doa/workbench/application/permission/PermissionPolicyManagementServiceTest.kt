package ink.doa.workbench.application.permission

import ink.doa.workbench.identity.permission.PermissionPolicyRecord
import ink.doa.workbench.identity.permission.PermissionPolicyRepository
import ink.doa.workbench.identity.permission.PermissionPolicyRuleRecord
import ink.doa.workbench.identity.permission.UpdatePermissionPolicyCommand
import ink.doa.workbench.identity.permission.model.AuthorizationAction
import ink.doa.workbench.identity.permission.model.PermissionEffect
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.ResourceConflictException
import ink.doa.workbench.kernel.common.ids.PublicId
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

    "createDocument stores tenant rules with stable positions" {
      val policy = samplePolicy(tenantId, "member-manager")
      val rule = sampleTenantRule(policy, "tenant.member.manage")
      coEvery { policies.findByCode(tenantId, policy.code) } returns null
      coEvery { policies.create(any()) } returns policy
      coEvery { policies.listRules(policy.id) } returns listOf(rule)

      val result = runBlocking {
        service.createDocument(
          CreatePermissionPolicyDocumentCommand(
            tenantId,
            1,
            policy.code,
            policy.name,
            null,
            listOf(tenantRule("tenant.member.manage")),
          )
        )
      }

      result.rules.single().position shouldBe 0
    }

    "createDocument rejects Agile actions" {
      coEvery { policies.findByCode(tenantId, any()) } returns null

      shouldThrow<InvalidRequestException> {
        runBlocking {
          service.createDocument(
            CreatePermissionPolicyDocumentCommand(
              tenantId,
              1,
              "agile",
              "Agile",
              null,
              listOf(tenantRule("issue.view", "issue:*")),
            )
          )
        }
      }
    }

    "createDocument rejects conditions" {
      shouldThrow<InvalidRequestException> {
        runBlocking {
          service.createDocument(
            CreatePermissionPolicyDocumentCommand(
              tenantId,
              1,
              "conditional",
              "Conditional",
              null,
              listOf(tenantRule("tenant.read").copy(conditionJson = "{}")),
            )
          )
        }
      }
    }

    "listPolicies hides non-tenant policies" {
      val tenantPolicy = samplePolicy(tenantId, "tenant-reader")
      val agilePolicy = samplePolicy(tenantId, "issue-reader")
      coEvery { policies.list(tenantId) } returns listOf(tenantPolicy, agilePolicy)
      coEvery { policies.listRules(tenantPolicy.id) } returns
        listOf(sampleTenantRule(tenantPolicy, "tenant.read"))
      coEvery { policies.listRules(agilePolicy.id) } returns
        listOf(sampleRule(agilePolicy, "issue.view", "issue:*"))

      val result = runBlocking { service.listPolicies(tenantId) }

      result.map { it.code } shouldBe listOf("tenant-reader")
    }

    "getPolicy rejects non-tenant policy" {
      val policy = samplePolicy(tenantId, "issue-reader")
      coEvery { policies.findByApiId(tenantId, policy.apiId.value) } returns policy
      coEvery { policies.listRules(policy.id) } returns
        listOf(sampleRule(policy, "issue.view", "issue:*"))

      shouldThrow<InvalidRequestException> {
        runBlocking { service.getPolicy(tenantId, policy.apiId.value) }
      }
    }

    "replaceDocument reports optimistic revision conflicts" {
      val policy = samplePolicy(tenantId, "custom")
      val existing = sampleTenantRule(policy, "tenant.read")
      coEvery { policies.findByApiId(tenantId, policy.apiId.value) } returns policy
      coEvery { policies.listRules(policy.id) } returns listOf(existing)
      coEvery { policies.replace(any()) } returns null

      shouldThrow<ResourceConflictException> {
        runBlocking {
          service.replaceDocument(
            ReplacePermissionPolicyDocumentCommand(
              tenantId,
              policy.apiId.value,
              1,
              policy.updatedAt.toString(),
              policy.code,
              "Changed",
              null,
              listOf(tenantRule("tenant.read").copy(id = existing.apiId.value)),
            )
          )
        }
      }
    }

    "replaceDocument cannot convert an Agile policy" {
      val policy = samplePolicy(tenantId, "issue-reader")
      coEvery { policies.findByApiId(tenantId, policy.apiId.value) } returns policy
      coEvery { policies.listRules(policy.id) } returns
        listOf(sampleRule(policy, "issue.view", "issue:*"))

      shouldThrow<InvalidRequestException> {
        runBlocking {
          service.replaceDocument(
            ReplacePermissionPolicyDocumentCommand(
              tenantId,
              policy.apiId.value,
              1,
              policy.updatedAt.toString(),
              policy.code,
              policy.name,
              null,
              listOf(tenantRule("tenant.read")),
            )
          )
        }
      }
    }

    "updatePolicy delegates for tenant custom policy" {
      val policy = samplePolicy(tenantId, "custom")
      val updated = policy.copy(name = "Renamed")
      coEvery { policies.findByApiId(tenantId, policy.apiId.value) } returns policy
      coEvery { policies.listRules(policy.id) } returns
        listOf(sampleTenantRule(policy, "tenant.read"))
      coEvery { policies.update(UpdatePermissionPolicyCommand(policy.id, "Renamed", null)) } returns
        updated

      val result = runBlocking {
        service.updatePolicy(tenantId, policy.apiId.value, "Renamed", null)
      }

      result.name shouldBe "Renamed"
    }

    "deletePolicy rejects policy with active bindings" {
      val policy = samplePolicy(tenantId, "custom")
      coEvery { policies.findByApiId(tenantId, policy.apiId.value) } returns policy
      coEvery { policies.listRules(policy.id) } returns
        listOf(sampleTenantRule(policy, "tenant.read"))
      coEvery { policies.hasActiveBindings(policy.id, any()) } returns true

      shouldThrow<InvalidRequestException> {
        runBlocking { service.deletePolicy(tenantId, policy.apiId.value) }
      }
    }

    "simulate applies deny precedence" {
      val result =
        service.simulate(
          tenantId,
          1,
          listOf(
            tenantRule("tenant.read"),
            tenantRule("tenant.read").copy(effect = PermissionEffect.DENY),
          ),
          "tenant.read",
        )

      result.decision shouldBe PermissionEffect.DENY
      result.reason shouldBe "matching_deny"
    }

    "simulate denies when no allow rule matches" {
      val result = service.simulate(tenantId, 1, listOf(tenantRule("tenant.read")), "tenant.update")

      result.decision shouldBe PermissionEffect.DENY
      result.reason shouldBe "no_matching_allow"
    }
  })

private fun tenantRule(
  action: String,
  resourcePattern: String = if (action.startsWith("tenant.")) "tenant:*" else "permission:*",
) =
  PermissionPolicyDocumentRuleCommand(
    id = null,
    action = action,
    resourcePattern = resourcePattern,
    effect = PermissionEffect.ALLOW,
    conditionJson = null,
  )

private fun sampleTenantRule(policy: PermissionPolicyRecord, action: String) =
  sampleRule(
    policy,
    action,
    if (action.startsWith("tenant.")) "tenant:*" else "permission:*",
  )

private fun sampleRule(
  policy: PermissionPolicyRecord,
  action: String,
  resourcePattern: String,
) =
  PermissionPolicyRuleRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("prr"),
    policyId = policy.id,
    action = AuthorizationAction(action),
    resourcePattern = resourcePattern,
    effect = PermissionEffect.ALLOW,
    conditionJson = null,
    position = 0,
    createdAt = policy.createdAt,
  )

private fun samplePolicy(
  tenantId: UUID,
  code: String,
  builtin: Boolean = false,
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
