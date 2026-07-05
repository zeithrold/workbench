package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.project.ProjectAccessService
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.TenantMemberRepository
import ink.doa.workbench.core.identity.model.TenantMemberRecord
import ink.doa.workbench.core.permission.PermissionBindingRepository
import ink.doa.workbench.core.permission.ResolvedPermissionRule
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.core.workitem.view.WorkItemViewDefaults
import ink.doa.workbench.core.workitem.view.WorkItemViewRecord
import ink.doa.workbench.core.workitem.view.WorkItemViewVisibility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class WorkItemViewAccessServiceTest :
  FunSpec({
    val bindings = mockk<PermissionBindingRepository>()
    val projectAccess = mockk<ProjectAccessService>()
    val tenantMembers = mockk<TenantMemberRepository>()
    val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
    val service =
      WorkItemViewAccessService(
        bindings = bindings,
        projectAccess = projectAccess,
        tenantMembers = tenantMembers,
        clock = clock,
      )

    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val ownerId = UUID.randomUUID()
    val otherUserId = UUID.randomUUID()
    val now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)

    fun view(
      visibility: WorkItemViewVisibility,
      project: UUID? = projectId,
      apiId: String = "wiv_01JABCDEFGHJKMNPQRSTVWXYZ0",
    ) =
      WorkItemViewRecord(
        id = UUID.randomUUID(),
        apiId = PublicId(apiId),
        tenantId = tenantId,
        projectId = project,
        ownerId = ownerId,
        ownerApiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
        name = "Sprint board",
        description = null,
        visibility = visibility,
        filterAst = WorkItemViewDefaults.EMPTY_FILTER,
        sortAst = WorkItemViewDefaults.EMPTY_SORT,
        groupAst = WorkItemViewDefaults.EMPTY_GROUP,
        displayFields = WorkItemViewDefaults.EMPTY_DISPLAY_FIELDS,
        createdAt = now,
        updatedAt = now,
      )

    beforeTest {
      coEvery {
        bindings.listActiveRulesForSubject(otherUserId, tenantId, projectId, now)
      } returns emptyList()
      coEvery {
        bindings.listActiveRulesForSubject(otherUserId, tenantId, null, now)
      } returns emptyList()
    }

    test("owner can always read and manage") {
      service.canRead(view(WorkItemViewVisibility.PRIVATE), ownerId) shouldBe true
      service.canManage(view(WorkItemViewVisibility.PRIVATE), ownerId) shouldBe true
    }

    test("private view is hidden from non-owner without binding") {
      service.canRead(view(WorkItemViewVisibility.PRIVATE), otherUserId) shouldBe false
    }

    test("project visibility allows project readers") {
      coEvery { projectAccess.isProjectMember(otherUserId, tenantId, projectId) } returns true
      coEvery { bindings.listActiveRulesForSubject(otherUserId, tenantId, projectId, now) } returns
        listOf(
          ResolvedPermissionRule(
            bindingId = UUID.randomUUID(),
            action = AuthorizationAction("issue.view"),
            resourcePattern = "issue:*",
            effect = PermissionEffect.ALLOW,
          )
        )

      service.canRead(view(WorkItemViewVisibility.PROJECT), otherUserId) shouldBe true
    }

    test("tenant visibility allows tenant members") {
      coEvery { tenantMembers.findByTenantAndUser(tenantId, otherUserId) } returns
        TenantMemberRecord(
          id = UUID.randomUUID(),
          apiId = PublicId("tmb_01JABCDEFGHJKMNPQRSTVWXYZ2"),
          tenantId = tenantId,
          userId = otherUserId,
          status = ink.doa.workbench.core.identity.model.TenantMemberStatus.ACTIVE,
          joinedAt = now,
          invitedBy = null,
          createdAt = now,
          updatedAt = now,
        )

      service.canRead(view(WorkItemViewVisibility.TENANT, project = null), otherUserId) shouldBe
        true
    }

    test("view.manage binding grants manage to non-owner") {
      coEvery { bindings.listActiveRulesForSubject(otherUserId, tenantId, projectId, now) } returns
        listOf(
          ResolvedPermissionRule(
            bindingId = UUID.randomUUID(),
            action = AuthorizationAction("view.manage"),
            resourcePattern = "view:wiv_01JABCDEFGHJKMNPQRSTVWXYZ0",
            effect = PermissionEffect.ALLOW,
          )
        )

      service.canManage(view(WorkItemViewVisibility.PRIVATE), otherUserId) shouldBe true
    }

    test("view.read binding grants read on private view") {
      coEvery { bindings.listActiveRulesForSubject(otherUserId, tenantId, projectId, now) } returns
        listOf(
          ResolvedPermissionRule(
            bindingId = UUID.randomUUID(),
            action = AuthorizationAction("view.read"),
            resourcePattern = "view:wiv_01JABCDEFGHJKMNPQRSTVWXYZ0",
            effect = PermissionEffect.ALLOW,
          )
        )

      service.canRead(view(WorkItemViewVisibility.PRIVATE), otherUserId) shouldBe true
    }

    test("create requires view.create on view star") {
      coEvery { bindings.listActiveRulesForSubject(otherUserId, tenantId, projectId, now) } returns
        listOf(
          ResolvedPermissionRule(
            bindingId = UUID.randomUUID(),
            action = AuthorizationAction("view.create"),
            resourcePattern = "view:*",
            effect = PermissionEffect.ALLOW,
          )
        )

      service.requireCreate(tenantId, projectId, otherUserId)
    }

    test("project visibility allows non-member read only visibility") {
      coEvery { projectAccess.isProjectMember(otherUserId, tenantId, projectId) } returns false
      coEvery {
        projectAccess.allowsVisibilityAction(
          otherUserId,
          tenantId,
          projectId,
          AuthorizationAction("issue.view"),
        )
      } returns true

      service.canRead(view(WorkItemViewVisibility.PROJECT), otherUserId) shouldBe true
    }
  })
