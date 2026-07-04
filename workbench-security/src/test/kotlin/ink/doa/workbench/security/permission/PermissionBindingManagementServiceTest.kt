package ink.doa.workbench.security.permission

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.core.permission.PermissionBindingRecord
import ink.doa.workbench.core.permission.PermissionBindingRepository
import ink.doa.workbench.core.permission.PermissionGroupRecord
import ink.doa.workbench.core.permission.PermissionPolicyRecord
import ink.doa.workbench.core.permission.PermissionPrincipalType
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.security.common.PublicIdResolver
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

class PermissionBindingManagementServiceTest :
  StringSpec({
    val bindings = mockk<PermissionBindingRepository>()
    val bindingViews = mockk<PermissionBindingViewAssembler>()
    val groupManagement = mockk<PermissionGroupManagementService>(relaxed = true)
    val policyManagement = mockk<PermissionPolicyManagementService>()
    val publicIds = mockk<PublicIdResolver>()
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val service =
      PermissionBindingManagementService(
        bindings,
        bindingViews,
        groupManagement,
        policyManagement,
        publicIds,
        clock,
      )
    val tenantId = UUID.randomUUID()

    "listBindings maps repository records through view assembler" {
      val binding = sampleBinding(tenantId)
      val view = sampleBindingView(binding)
      coEvery { bindings.listByTenant(tenantId) } returns listOf(binding)
      coEvery { bindingViews.assemble(tenantId, binding) } returns view

      val result = runBlocking { service.listBindings(tenantId) }

      result.single().id shouldBe binding.apiId.value
    }

    "createBinding creates USER principal binding" {
      val user = sampleUser()
      val policy = samplePolicy(tenantId)
      val binding = sampleBinding(tenantId, userId = user.id, policyId = policy.id)
      val view = sampleBindingView(binding, user = user)
      coEvery { publicIds.resolveUser(user.apiId.value) } returns user
      coEvery { policyManagement.requirePolicy(tenantId, policy.apiId.value) } returns policy
      coEvery { bindings.create(any()) } returns binding
      coEvery { bindingViews.assemble(tenantId, binding) } returns view

      val result = runBlocking {
        service.createBinding(
          CreateManagedPermissionBindingCommand(
            tenantId = tenantId,
            principalType = PermissionPrincipalType.USER,
            userPublicId = user.apiId.value,
            groupPublicId = null,
            policyPublicId = policy.apiId.value,
            projectPublicId = null,
            effect = null,
            actorUserId = UUID.randomUUID(),
          )
        )
      }

      result.id shouldBe binding.apiId.value
      result.user?.id shouldBe user.apiId.value
      coVerify {
        bindings.create(match { it.principalUserId == user.id && it.policyId == policy.id })
      }
    }

    "createBinding rejects unsupported effect" {
      shouldThrow<InvalidRequestException> {
        runBlocking {
          service.createBinding(
            CreateManagedPermissionBindingCommand(
              tenantId = tenantId,
              principalType = PermissionPrincipalType.USER,
              userPublicId = "usr_test",
              groupPublicId = null,
              policyPublicId = "pol_test",
              projectPublicId = null,
              effect = PermissionEffect.DENY,
              actorUserId = null,
            )
          )
        }
      }
    }

    "expireBinding throws when binding is missing" {
      coEvery { bindings.findByApiId(tenantId, "pbd_missing") } returns null

      shouldThrow<ResourceNotFoundException> {
        runBlocking { service.expireBinding(tenantId, "pbd_missing") }
      }
    }

    "createBinding creates GROUP principal binding" {
      val group = sampleGroup(tenantId)
      val policy = samplePolicy(tenantId)
      val binding =
        sampleBinding(tenantId, userId = null, policyId = policy.id)
          .copy(
            principalType = PermissionPrincipalType.GROUP,
            principalGroupId = group.id,
          )
      val view = sampleBindingView(binding)
      coEvery { groupManagement.requireGroup(tenantId, group.apiId.value) } returns group
      coEvery { policyManagement.requirePolicy(tenantId, policy.apiId.value) } returns policy
      coEvery { bindings.create(any()) } returns binding
      coEvery { bindingViews.assemble(tenantId, binding) } returns view

      val result = runBlocking {
        service.createBinding(
          CreateManagedPermissionBindingCommand(
            tenantId = tenantId,
            principalType = PermissionPrincipalType.GROUP,
            userPublicId = null,
            groupPublicId = group.apiId.value,
            policyPublicId = policy.apiId.value,
            projectPublicId = null,
            effect = null,
            actorUserId = UUID.randomUUID(),
          )
        )
      }

      result.id shouldBe binding.apiId.value
    }

    "createBinding creates TENANT_MEMBER principal binding" {
      val policy = samplePolicy(tenantId)
      val binding =
        sampleBinding(tenantId, userId = null, policyId = policy.id)
          .copy(principalType = PermissionPrincipalType.TENANT_MEMBER)
      val view = sampleBindingView(binding)
      coEvery { policyManagement.requirePolicy(tenantId, policy.apiId.value) } returns policy
      coEvery { bindings.create(any()) } returns binding
      coEvery { bindingViews.assemble(tenantId, binding) } returns view

      val result = runBlocking {
        service.createBinding(
          CreateManagedPermissionBindingCommand(
            tenantId = tenantId,
            principalType = PermissionPrincipalType.TENANT_MEMBER,
            userPublicId = null,
            groupPublicId = null,
            policyPublicId = policy.apiId.value,
            projectPublicId = null,
            effect = null,
            actorUserId = null,
          )
        )
      }

      result.principalType shouldBe "TENANT_MEMBER"
    }

    "createBinding rejects invalid user principal target" {
      val group = sampleGroup(tenantId)
      val policy = samplePolicy(tenantId)
      coEvery { groupManagement.requireGroup(tenantId, group.apiId.value) } returns group
      coEvery { policyManagement.requirePolicy(tenantId, policy.apiId.value) } returns policy

      shouldThrow<InvalidRequestException> {
        runBlocking {
          service.createBinding(
            CreateManagedPermissionBindingCommand(
              tenantId = tenantId,
              principalType = PermissionPrincipalType.USER,
              userPublicId = null,
              groupPublicId = group.apiId.value,
              policyPublicId = policy.apiId.value,
              projectPublicId = null,
              effect = null,
              actorUserId = null,
            )
          )
        }
      }
    }

    "expireBinding delegates to repository" {
      val binding = sampleBinding(tenantId)
      coEvery { bindings.findByApiId(tenantId, binding.apiId.value) } returns binding
      coEvery { bindings.expire(tenantId, binding.id, any()) } returns true

      runBlocking { service.expireBinding(tenantId, binding.apiId.value) } shouldBe true
    }
  })

private fun sampleGroup(tenantId: UUID): PermissionGroupRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return PermissionGroupRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("pgr"),
    tenantId = tenantId,
    code = "developers",
    name = "Developers",
    description = null,
    builtin = false,
    createdAt = now,
    updatedAt = now,
  )
}

private fun sampleUser(): UserRecord =
  UserRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("usr"),
    displayName = "Ada",
    primaryEmail = "ada@example.test",
  )

private fun samplePolicy(tenantId: UUID): PermissionPolicyRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return PermissionPolicyRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("pol"),
    tenantId = tenantId,
    code = "custom",
    name = "Custom",
    description = null,
    builtin = false,
    createdAt = now,
    updatedAt = now,
  )
}

private fun sampleBinding(
  tenantId: UUID,
  userId: UUID? = null,
  policyId: UUID = UUID.randomUUID(),
): PermissionBindingRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return PermissionBindingRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("pbd"),
    tenantId = tenantId,
    projectId = null,
    principalType = PermissionPrincipalType.USER,
    principalUserId = userId,
    principalGroupId = null,
    policyId = policyId,
    validFrom = now,
    validTo = null,
    createdBy = null,
    createdAt = now,
  )
}

private fun sampleBindingView(
  binding: PermissionBindingRecord,
  user: UserRecord? = null,
): PermissionBindingView {
  val policy = samplePolicy(binding.tenantId).copy(id = binding.policyId)
  return PermissionBindingView(
    id = binding.apiId.value,
    principalType = binding.principalType.name,
    user = user?.let(UserSummary::from),
    group = null,
    policy = PermissionPolicySummary.from(policy),
    project = null,
  )
}
