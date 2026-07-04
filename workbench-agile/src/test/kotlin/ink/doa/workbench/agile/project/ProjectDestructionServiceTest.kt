package ink.doa.workbench.agile.project

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.project.ProjectDestructionRepository
import ink.doa.workbench.core.project.ProjectRepository
import ink.doa.workbench.core.project.model.ProjectRecord
import ink.doa.workbench.core.project.model.ProjectStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ProjectDestructionServiceTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val actorId = UUID.randomUUID()
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)

    "execute returns false when project is missing" {
      val projects = mockk<ProjectRepository>()
      val destruction = mockk<ProjectDestructionRepository>(relaxed = true)
      coEvery { projects.findById(tenantId, projectId) } returns null
      val service = ProjectDestructionService(projects, destruction, clock)

      service.execute(tenantId, projectId, actorId, "cleanup") shouldBe false
      coVerify(exactly = 0) {
        destruction.softDeleteProjectScopedData(any(), any(), any(), any(), any())
      }
    }

    "execute returns false when project is not destroying" {
      val projects = mockk<ProjectRepository>()
      val destruction = mockk<ProjectDestructionRepository>(relaxed = true)
      coEvery { projects.findById(tenantId, projectId) } returns
        ProjectRecord(
          id = projectId,
          apiId = PublicId.new("prj"),
          tenantId = tenantId,
          identifier = "CORE",
          name = "Core",
          description = null,
          status = ProjectStatus.ACTIVE,
          leadUserId = actorId,
          createdBy = actorId,
        )
      val service = ProjectDestructionService(projects, destruction, clock)

      service.execute(tenantId, projectId, actorId, "cleanup") shouldBe false
    }

    "execute destroys project data when status is destroying" {
      val projects = mockk<ProjectRepository>()
      val destruction = mockk<ProjectDestructionRepository>()
      coEvery { projects.findById(tenantId, projectId) } returns
        ProjectRecord(
          id = projectId,
          apiId = PublicId.new("prj"),
          tenantId = tenantId,
          identifier = "CORE",
          name = "Core",
          description = null,
          status = ProjectStatus.DESTROYING,
          leadUserId = actorId,
          createdBy = actorId,
        )
      coEvery { destruction.expireBindingsByProject(tenantId, projectId, any()) } returns 1
      coEvery {
        destruction.softDeleteProjectScopedData(
          tenantId = tenantId,
          projectId = projectId,
          deletedAt = any(),
          deletedBy = actorId,
          deleteReason = "cleanup",
        )
      } returns Unit
      coEvery {
        projects.finalizeDestroy(
          tenantId = tenantId,
          projectId = projectId,
          deletedAt = any(),
          deletedBy = actorId,
          deleteReason = "cleanup",
        )
      } returns true
      val service = ProjectDestructionService(projects, destruction, clock)

      service.execute(tenantId, projectId, actorId, "cleanup") shouldBe true
      coVerify { destruction.expireBindingsByProject(tenantId, projectId, any()) }
    }
  })
