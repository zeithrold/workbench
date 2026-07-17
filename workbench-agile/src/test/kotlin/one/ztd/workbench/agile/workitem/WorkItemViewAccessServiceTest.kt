package one.ztd.workbench.agile.workitem

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.agile.project.ProjectAccessService
import one.ztd.workbench.agile.workitem.view.WorkItemViewDefaults
import one.ztd.workbench.agile.workitem.view.WorkItemViewRecord
import one.ztd.workbench.agile.workitem.view.WorkItemViewVisibility
import one.ztd.workbench.identity.TenantMemberRepository
import one.ztd.workbench.identity.model.TenantMemberRecord
import one.ztd.workbench.identity.permission.PermissionBindingRepository
import one.ztd.workbench.identity.permission.ResolvedPermissionRule
import one.ztd.workbench.identity.permission.model.AuthorizationAction
import one.ztd.workbench.identity.permission.model.PermissionEffect
import one.ztd.workbench.kernel.common.errors.PermissionDeniedException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.ids.PublicId

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
        queryAst = WorkItemViewDefaults.EMPTY_QUERY,
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
          status = one.ztd.workbench.identity.model.TenantMemberStatus.ACTIVE,
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

    test("requireCreate throws when access denied") {
      shouldThrow<PermissionDeniedException> {
          service.requireCreate(tenantId, projectId, otherUserId)
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_VIEW_CREATE_DENIED
    }

    test("requireRead throws when access denied") {
      shouldThrow<PermissionDeniedException> {
          service.requireRead(view(WorkItemViewVisibility.PRIVATE), otherUserId)
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_VIEW_READ_DENIED
    }

    test("requireManage throws when access denied") {
      shouldThrow<PermissionDeniedException> {
          service.requireManage(view(WorkItemViewVisibility.PRIVATE), otherUserId)
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_VIEW_MANAGE_DENIED
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
