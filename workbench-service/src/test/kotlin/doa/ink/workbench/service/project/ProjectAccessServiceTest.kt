package doa.ink.workbench.service.project

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.permission.PermissionBindingRepository
import doa.ink.workbench.core.permission.ResolvedPermissionRule
import doa.ink.workbench.core.permission.model.AuthorizationAction
import doa.ink.workbench.core.permission.model.PermissionEffect
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.core.project.model.NonMemberVisibility
import doa.ink.workbench.core.project.model.ProjectRecord
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
