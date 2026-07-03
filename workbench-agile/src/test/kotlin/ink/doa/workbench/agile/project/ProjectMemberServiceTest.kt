package ink.doa.workbench.agile.project

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.TenantMemberRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.model.TenantMemberRecord
import ink.doa.workbench.core.identity.model.TenantMemberStatus
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.core.permission.CreatePermissionBindingCommand
import ink.doa.workbench.core.permission.PermissionBindingRecord
import ink.doa.workbench.core.permission.PermissionBindingRepository
import ink.doa.workbench.core.permission.PermissionPolicyRecord
import ink.doa.workbench.core.permission.PermissionPolicyRepository
import ink.doa.workbench.core.permission.PermissionPrincipalType
import ink.doa.workbench.core.project.ProjectRepository
import ink.doa.workbench.core.project.model.NonMemberJoinPolicy
import ink.doa.workbench.core.project.model.ProjectRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
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

class ProjectMemberServiceTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val userId = UUID.randomUUID()
    val actorUserId = UUID.randomUUID()
    val now = OffsetDateTime.ofInstant(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC)
    val clock = Clock.fixed(now.toInstant(), ZoneOffset.UTC)

    val bindings = mockk<PermissionBindingRepository>()
    val policies = mockk<PermissionPolicyRepository>()
    val projects = mockk<ProjectRepository>()
    val users = mockk<UserRepository>()
    val tenantMembers = mockk<TenantMemberRepository>()

    val service = ProjectMemberService(bindings, policies, projects, users, tenantMembers, clock)

    val userPublicId = PublicId.new("usr")
    val user =
      UserRecord(
        id = userId,
        apiId = userPublicId,
        displayName = "Ada",
        primaryEmail = "ada@example.com",
      )

    val memberPolicyId = UUID.randomUUID()
    val memberPolicyPublicId = PublicId.new("pol")
    val memberPolicy =
      PermissionPolicyRecord(
        id = memberPolicyId,
        apiId = memberPolicyPublicId,
        tenantId = tenantId,
        code = "project-member",
        name = "Project Member",
        description = null,
        builtin = true,
        createdAt = now,
        updatedAt = now,
      )

    val bindingId = UUID.randomUUID()
    val bindingPublicId = PublicId.new("pbn")
    val binding =
      PermissionBindingRecord(
        id = bindingId,
        apiId = bindingPublicId,
        tenantId = tenantId,
        projectId = projectId,
        principalType = PermissionPrincipalType.USER,
        principalUserId = userId,
        principalGroupId = null,
        policyId = memberPolicyId,
        validFrom = now,
        validTo = null,
        createdBy = actorUserId,
        createdAt = now,
      )

    fun activeMembership() =
      TenantMemberRecord(
        id = UUID.randomUUID(),
        apiId = PublicId.new("tmb"),
        tenantId = tenantId,
        userId = userId,
        status = TenantMemberStatus.ACTIVE,
        joinedAt = now,
        invitedBy = null,
        createdAt = now,
        updatedAt = now,
      )

    fun stubActiveMemberLookup() {
      coEvery { users.findByApiId(userPublicId.value) } returns user
      coEvery { tenantMembers.findByTenantAndUser(tenantId, userId) } returns activeMembership()
      coEvery { users.findById(userId) } returns user
    }

    fun stubListMembers() {
      coEvery { bindings.listByProject(tenantId, projectId) } returns listOf(binding)
      coEvery { users.findById(userId) } returns user
      coEvery { policies.findById(tenantId, memberPolicyId) } returns memberPolicy
    }

    "listMembers returns active user bindings with policy summaries" {
      stubListMembers()

      val members = service.listMembers(tenantId, projectId)

      members.shouldHaveSize(1)
      members.single().user.id shouldBe userPublicId.value
      members.single().policies.shouldHaveSize(1)
      members.single().policies.single().bindingId shouldBe bindingPublicId.value
      members.single().policies.single().policy.code shouldBe "project-member"
      members.single().policies.single().policy.name shouldBe "Project Member"
    }

    "listMembers rejects bindings whose user record is missing" {
      coEvery { bindings.listByProject(tenantId, projectId) } returns listOf(binding)
      coEvery { users.findById(userId) } returns null

      shouldThrow<ResourceNotFoundException> { service.listMembers(tenantId, projectId) }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND
    }

    "addMember creates a binding when role resolves to a builtin policy" {
      stubActiveMemberLookup()
      coEvery { policies.findByCode(tenantId, "project-member") } returns memberPolicy
      val commandSlot = slot<CreatePermissionBindingCommand>()
      coEvery { bindings.create(capture(commandSlot)) } returns binding
      stubListMembers()

      val member =
        service.addMember(tenantId, projectId, userPublicId.value, null, "member", actorUserId)

      member.user.id shouldBe userPublicId.value
      commandSlot.captured.policyId shouldBe memberPolicyId
      commandSlot.captured.principalUserId shouldBe userId
      commandSlot.captured.createdBy shouldBe actorUserId
    }

    "addMember resolves explicit policy public id" {
      stubActiveMemberLookup()
      coEvery { policies.findByApiId(tenantId, memberPolicyPublicId.value) } returns memberPolicy
      coEvery { bindings.create(any()) } returns binding
      stubListMembers()

      service.addMember(
        tenantId,
        projectId,
        userPublicId.value,
        memberPolicyPublicId.value,
        null,
        actorUserId,
      )

      coVerify { policies.findByApiId(tenantId, memberPolicyPublicId.value) }
    }

    "addMember rejects unknown users" {
      coEvery { users.findByApiId(userPublicId.value) } returns null

      shouldThrow<ResourceNotFoundException> {
          service.addMember(tenantId, projectId, userPublicId.value, null, "member", actorUserId)
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND
    }

    "addMember rejects inactive tenant members" {
      coEvery { users.findByApiId(userPublicId.value) } returns user
      coEvery { tenantMembers.findByTenantAndUser(tenantId, userId) } returns
        activeMembership().copy(status = TenantMemberStatus.SUSPENDED)

      shouldThrow<InvalidRequestException> {
          service.addMember(tenantId, projectId, userPublicId.value, null, "member", actorUserId)
        }
        .errorCode shouldBe WorkbenchErrorCode.PROJECT_MEMBER_INACTIVE_TENANT_MEMBER
    }

    "addMember rejects unknown builtin roles" {
      stubActiveMemberLookup()

      shouldThrow<InvalidRequestException> {
          service.addMember(tenantId, projectId, userPublicId.value, null, "owner", actorUserId)
        }
        .errorCode shouldBe WorkbenchErrorCode.PROJECT_MEMBER_UNKNOWN_ROLE
    }

    "addMember requires either policy or role" {
      stubActiveMemberLookup()

      shouldThrow<InvalidRequestException> {
          service.addMember(tenantId, projectId, userPublicId.value, null, null, actorUserId)
        }
        .errorCode shouldBe WorkbenchErrorCode.PROJECT_MEMBER_POLICY_OR_ROLE_REQUIRED
    }

    "attachPolicy delegates to addMember after membership check" {
      stubActiveMemberLookup()
      coEvery { policies.findByCode(tenantId, "project-viewer") } returns
        memberPolicy.copy(code = "project-viewer", name = "Project Viewer")
      coEvery { bindings.create(any()) } returns binding
      stubListMembers()

      service.attachPolicy(tenantId, projectId, userPublicId.value, null, "viewer", actorUserId)

      coVerify { policies.findByCode(tenantId, "project-viewer") }
    }

    "removePolicy expires the binding" {
      coEvery { bindings.findByApiId(tenantId, bindingPublicId.value) } returns binding
      coEvery { bindings.expire(tenantId, bindingId, now) } returns true

      service.removePolicy(tenantId, bindingPublicId.value) shouldBe true

      coVerify { bindings.expire(tenantId, bindingId, now) }
    }

    "removePolicy rejects missing bindings" {
      coEvery { bindings.findByApiId(tenantId, bindingPublicId.value) } returns null

      shouldThrow<ResourceNotFoundException> {
          service.removePolicy(tenantId, bindingPublicId.value)
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_PERMISSION_BINDING_NOT_FOUND
    }

    "join adds project-member policy when self-join is open" {
      val openProject =
        ProjectRecord(
          id = projectId,
          apiId = PublicId.new("prj"),
          tenantId = tenantId,
          identifier = "CORE",
          name = "Core",
          description = null,
          nonMemberJoinPolicy = NonMemberJoinPolicy.OPEN,
        )
      coEvery { projects.findById(tenantId, projectId) } returns openProject
      coEvery { tenantMembers.findByTenantAndUser(tenantId, userId) } returns activeMembership()
      coEvery { users.findById(userId) } returns user
      coEvery { policies.findByCode(tenantId, "project-member") } returns memberPolicy
      coEvery { bindings.create(any()) } returns binding
      stubListMembers()

      val member = service.join(tenantId, projectId, userId, actorUserId)

      member.user.id shouldBe userPublicId.value
      coVerify { bindings.create(any()) }
    }

    "join rejects projects that disable self-join" {
      coEvery { projects.findById(tenantId, projectId) } returns
        ProjectRecord(
          id = projectId,
          apiId = PublicId.new("prj"),
          tenantId = tenantId,
          identifier = "CORE",
          name = "Core",
          description = null,
          nonMemberJoinPolicy = NonMemberJoinPolicy.ADMIN_ONLY,
        )

      shouldThrow<InvalidRequestException> {
          service.join(tenantId, projectId, userId, actorUserId)
        }
        .errorCode shouldBe WorkbenchErrorCode.PROJECT_SELF_JOIN_DISABLED
    }

    "join rejects missing projects" {
      coEvery { projects.findById(tenantId, projectId) } returns null

      shouldThrow<ResourceNotFoundException> {
          service.join(tenantId, projectId, userId, actorUserId)
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND
    }
  })
