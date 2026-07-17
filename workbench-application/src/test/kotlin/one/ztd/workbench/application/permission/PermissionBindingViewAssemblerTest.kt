package one.ztd.workbench.application.permission

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import one.ztd.workbench.agile.project.ProjectRepository
import one.ztd.workbench.agile.project.ProjectSummary
import one.ztd.workbench.agile.project.model.ProjectRecord
import one.ztd.workbench.agile.project.model.ProjectStatus
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.identity.permission.PermissionBindingRecord
import one.ztd.workbench.identity.permission.PermissionGroupRecord
import one.ztd.workbench.identity.permission.PermissionGroupRepository
import one.ztd.workbench.identity.permission.PermissionPolicyRecord
import one.ztd.workbench.identity.permission.PermissionPolicyRepository
import one.ztd.workbench.identity.permission.PermissionPrincipalType
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.ids.PublicId

class PermissionBindingViewAssemblerTest :
  StringSpec({
    val groups = mockk<PermissionGroupRepository>()
    val policies = mockk<PermissionPolicyRepository>()
    val users = mockk<UserRepository>()
    val projects = mockk<ProjectRepository>()
    val assembler = PermissionBindingViewAssembler(groups, policies, users, projects)
    val tenantId = UUID.randomUUID()
    val userId = UUID.randomUUID()
    val groupId = UUID.randomUUID()
    val policyId = UUID.randomUUID()
    val projectId = UUID.randomUUID()

    val user =
      UserRecord(
        id = userId,
        apiId = PublicId.new("usr"),
        displayName = "Ada",
        primaryEmail = "ada@example.test",
      )
    val policy =
      PermissionPolicyRecord(
        id = policyId,
        apiId = PublicId.new("pol"),
        tenantId = tenantId,
        code = "viewer",
        name = "Viewer",
        description = null,
        builtin = false,
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )
    val group =
      PermissionGroupRecord(
        id = groupId,
        apiId = PublicId.new("pgr"),
        tenantId = tenantId,
        code = "devs",
        name = "Developers",
        description = null,
        builtin = false,
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )
    val project =
      ProjectRecord(
        id = projectId,
        apiId = PublicId.new("prj"),
        tenantId = tenantId,
        identifier = "CORE",
        name = "Core",
        description = null,
        status = ProjectStatus.ACTIVE,
        leadUserId = userId,
        createdBy = userId,
      )

    "assemble maps user principal binding" {
      val binding =
        sampleBinding(
          SampleBindingParams(
            tenantId = tenantId,
            userId = userId,
            policyId = policyId,
          )
        )
      coEvery { policies.findById(tenantId, policyId) } returns policy
      coEvery { users.findById(userId) } returns user

      val view = runBlocking { assembler.assemble(tenantId, binding) }

      view.user shouldBe UserSummary.from(user)
      view.policy.code shouldBe "viewer"
      view.group shouldBe null
      view.project shouldBe null
    }

    "assemble maps group and project principals" {
      val binding =
        sampleBinding(
          SampleBindingParams(
            tenantId = tenantId,
            groupId = groupId,
            policyId = policyId,
            projectId = projectId,
            principalType = PermissionPrincipalType.GROUP,
          )
        )
      coEvery { policies.findById(tenantId, policyId) } returns policy
      coEvery { groups.findById(tenantId, groupId) } returns group
      coEvery { projects.findById(tenantId, projectId) } returns project

      val view = runBlocking { assembler.assemble(tenantId, binding) }

      view.group?.code shouldBe "devs"
      view.project shouldBe ProjectSummary.from(project)
    }

    "assemble throws when policy missing" {
      val binding =
        sampleBinding(
          SampleBindingParams(
            tenantId = tenantId,
            userId = userId,
            policyId = policyId,
          )
        )
      coEvery { policies.findById(tenantId, policyId) } returns null

      shouldThrow<ResourceNotFoundException> {
          runBlocking { assembler.assemble(tenantId, binding) }
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_PERMISSION_POLICY_NOT_FOUND
    }
  })

private data class SampleBindingParams(
  val tenantId: UUID,
  val userId: UUID? = null,
  val groupId: UUID? = null,
  val policyId: UUID = UUID.randomUUID(),
  val projectId: UUID? = null,
  val principalType: PermissionPrincipalType? = null,
)

private fun sampleBinding(params: SampleBindingParams): PermissionBindingRecord {
  val principalType =
    params.principalType
      ?: if (params.userId != null) PermissionPrincipalType.USER else PermissionPrincipalType.GROUP
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return PermissionBindingRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("pbd"),
    tenantId = params.tenantId,
    principalType = principalType,
    principalUserId = params.userId,
    principalGroupId = params.groupId,
    policyId = params.policyId,
    projectId = params.projectId,
    validFrom = now,
    validTo = null,
    createdBy = null,
    createdAt = now,
  )
}
