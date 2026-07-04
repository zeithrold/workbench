package ink.doa.workbench.agile.project

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.permission.PermissionBindingRepository
import ink.doa.workbench.core.permission.ResolvedPermissionRule
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.core.project.ProjectRepository
import ink.doa.workbench.core.project.model.NonMemberVisibility
import ink.doa.workbench.core.project.model.ProjectRecord
import ink.doa.workbench.core.project.model.ProjectStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ProjectAccessServiceTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val userId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val clock = Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC)
    val projects = mockk<ProjectRepository>()
    val bindings = mockk<PermissionBindingRepository>()
    val service = ProjectAccessService(projects, bindings, clock)

    val openProject =
      ProjectRecord(
        id = projectId,
        apiId = PublicId.new("prj"),
        tenantId = tenantId,
        identifier = "CORE",
        name = "Core",
        description = null,
        status = ProjectStatus.ACTIVE,
        nonMemberVisibility = NonMemberVisibility.READ_ONLY,
      )

    "listVisibleProjects includes visibility-open projects for non-admin members" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, null, any()) } returns
        emptyList()
      coEvery { bindings.listProjectIdsForSubject(tenantId, userId, any()) } returns emptySet()
      coEvery { projects.list(tenantId, null) } returns listOf(openProject)

      service.listVisibleProjects(userId, tenantId, null).shouldHaveSize(1)
    }

    "allowsVisibilityAction grants read for read_only visibility" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, null, any()) } returns
        emptyList()
      coEvery { bindings.listProjectIdsForSubject(tenantId, userId, any()) } returns emptySet()
      coEvery { projects.findById(tenantId, projectId) } returns openProject

      service.allowsVisibilityAction(
        userId,
        tenantId,
        projectId,
        AuthorizationAction("project.read"),
      ) shouldBe true
    }

    "allowsVisibilityAction grants issue.view for read_only visibility" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, null, any()) } returns
        emptyList()
      coEvery { bindings.listProjectIdsForSubject(tenantId, userId, any()) } returns emptySet()
      coEvery { projects.findById(tenantId, projectId) } returns openProject

      service.allowsVisibilityAction(
        userId,
        tenantId,
        projectId,
        AuthorizationAction("issue.view"),
      ) shouldBe true
    }

    "allowsVisibilityAction grants issue.update for read_write visibility" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, null, any()) } returns
        emptyList()
      coEvery { bindings.listProjectIdsForSubject(tenantId, userId, any()) } returns emptySet()
      coEvery { projects.findById(tenantId, projectId) } returns
        openProject.copy(nonMemberVisibility = NonMemberVisibility.READ_WRITE)

      service.allowsVisibilityAction(
        userId,
        tenantId,
        projectId,
        AuthorizationAction("issue.update"),
      ) shouldBe true
    }

    "hasTenantWideProjectAccess detects tenant-level project.read" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, null, any()) } returns
        listOf(
          ResolvedPermissionRule(
            bindingId = UUID.randomUUID(),
            action = AuthorizationAction("project.read"),
            resourcePattern = "project:*",
            effect = PermissionEffect.ALLOW,
          )
        )

      service.hasTenantWideProjectAccess(userId, tenantId) shouldBe true
    }
  })
