package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.core.permission.PermissionBindingRecord
import ink.doa.workbench.core.permission.PermissionBindingRepository
import ink.doa.workbench.core.permission.PermissionGroupRepository
import ink.doa.workbench.core.permission.PermissionPolicyRecord
import ink.doa.workbench.core.permission.PermissionPolicyRepository
import ink.doa.workbench.core.permission.PermissionPrincipalType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class WorkItemAccessPrincipalResolverTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val actorId = UUID.randomUUID()
    val groupId = UUID.randomUUID()
    val policyId = UUID.randomUUID()
    val clock = Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC)
    val bindings = mockk<PermissionBindingRepository>()
    val groups = mockk<PermissionGroupRepository>()
    val policies = mockk<PermissionPolicyRepository>()
    val users = mockk<UserRepository>()
    val actorApiId = PublicId.new("usr").value
    coEvery { users.findById(actorId) } returns
      UserRecord(
        id = actorId,
        apiId = PublicId(actorApiId),
        displayName = "Actor",
        primaryEmail = "actor@example.com",
      )
    val resolver = WorkItemAccessPrincipalResolver(bindings, groups, policies, users, clock)

    coEvery { groups.listActiveGroupIdsForUser(tenantId, actorId) } returns setOf(groupId)

    "resolveActor returns group ids and mapped project roles" {
      val now = OffsetDateTime.parse("2026-07-01T00:00:00Z")
      coEvery { bindings.listByProject(tenantId, projectId) } returns
        listOf(
          binding(
            BindingFixture(
              principalType = PermissionPrincipalType.GROUP,
              principalGroupId = groupId,
              policyId = policyId,
              validFrom = now.minusDays(1),
            )
          )
        )
      coEvery { policies.findById(tenantId, policyId) } returns
        policyRecord(tenantId, policyId, code = "project-admin")

      val actor = resolver.resolveActor(tenantId, projectId, actorId)

      actor.userId shouldBe actorId
      actor.userApiId shouldBe actorApiId
      actor.groupIds shouldBe setOf(groupId)
      actor.projectRoles shouldBe setOf("admin")
    }

    "ignores expired bindings when resolving roles" {
      val now = OffsetDateTime.parse("2026-07-01T00:00:00Z")
      coEvery { bindings.listByProject(tenantId, projectId) } returns
        listOf(
          binding(
            BindingFixture(
              principalType = PermissionPrincipalType.USER,
              principalUserId = actorId,
              policyId = policyId,
              validFrom = now.minusDays(10),
              validTo = now.minusDays(1),
            )
          )
        )

      val actor = resolver.resolveActor(tenantId, projectId, actorId)

      actor.projectRoles shouldBe emptySet()
    }

    "tenant member binding grants role without group match" {
      val now = OffsetDateTime.parse("2026-07-01T00:00:00Z")
      coEvery { bindings.listByProject(tenantId, projectId) } returns
        listOf(
          binding(
            BindingFixture(
              principalType = PermissionPrincipalType.TENANT_MEMBER,
              policyId = policyId,
              validFrom = now.minusDays(1),
            )
          )
        )
      coEvery { policies.findById(tenantId, policyId) } returns
        policyRecord(tenantId, policyId, code = "project-member")

      resolver.resolveActor(tenantId, projectId, actorId).projectRoles shouldBe setOf("member")
    }
  })

private data class BindingFixture(
  val principalType: PermissionPrincipalType,
  val policyId: UUID,
  val validFrom: OffsetDateTime,
  val principalUserId: UUID? = null,
  val principalGroupId: UUID? = null,
  val validTo: OffsetDateTime? = null,
)

private fun binding(fixture: BindingFixture): PermissionBindingRecord {
  val now = OffsetDateTime.parse("2026-07-01T00:00:00Z")
  return PermissionBindingRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("pbd"),
    tenantId = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    principalType = fixture.principalType,
    principalUserId = fixture.principalUserId,
    principalGroupId = fixture.principalGroupId,
    policyId = fixture.policyId,
    validFrom = fixture.validFrom,
    validTo = fixture.validTo,
    createdBy = null,
    createdAt = now,
  )
}

private fun policyRecord(
  tenantId: UUID,
  policyId: UUID,
  code: String,
): PermissionPolicyRecord {
  val now = OffsetDateTime.parse("2026-07-01T00:00:00Z")
  return PermissionPolicyRecord(
    id = policyId,
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
