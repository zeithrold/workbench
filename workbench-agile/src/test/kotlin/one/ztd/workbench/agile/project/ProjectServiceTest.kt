package one.ztd.workbench.agile.project

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import one.ztd.workbench.agile.project.events.ProjectDestroyRequestedEvent
import one.ztd.workbench.agile.project.model.CreateProjectCommand
import one.ztd.workbench.agile.project.model.ProjectRecord
import one.ztd.workbench.kernel.common.ids.PublicId

class ProjectServiceTest :
  StringSpec({
    "create delegates to the project repository port" {
      val command =
        CreateProjectCommand(
          tenantId = UUID.randomUUID(),
          identifier = "CORE",
          name = "Core Platform",
          description = "Platform work",
          createdBy = UUID.randomUUID(),
          leadUserId = UUID.randomUUID(),
        )
      val record =
        ProjectRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("prj"),
          tenantId = command.tenantId,
          identifier = command.identifier,
          name = command.name,
          description = command.description,
        )
      val repository = mockk<ProjectRepository>()
      coEvery { repository.create(command) } returns record

      val projectResolver = ProjectResolver(repository)

      val result = ProjectService(repository, projectResolver).create(command)

      result shouldBe record
      coVerify(exactly = 1) { repository.create(command) }
    }

    "list get archive and unarchive delegate to repository ports" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
      val record =
        ProjectRecord(
          id = projectId,
          apiId = PublicId.new("prj"),
          tenantId = tenantId,
          identifier = "CORE",
          name = "Core",
          description = null,
        )
      val repository = mockk<ProjectRepository>()
      val resolver = mockk<ProjectResolver>()
      coEvery { repository.list(tenantId, "CORE") } returns listOf(record)
      coEvery { resolver.resolveProject(tenantId, record.apiId.value) } returns record
      coEvery { repository.markArchived(tenantId, projectId, now, actorId) } returns record
      coEvery { repository.markActive(tenantId, projectId) } returns record
      val service = ProjectService(repository, resolver)

      service.list(tenantId, "CORE").single().identifier shouldBe "CORE"
      service.get(tenantId, record.apiId.value).id shouldBe projectId
      service.archive(tenantId, projectId, now, actorId).name shouldBe "Core"
      service.unarchive(tenantId, projectId).identifier shouldBe "CORE"
      coEvery {
        repository.markDestroying(tenantId, projectId, actorId, "cleanup")
      } returns record.copy(status = one.ztd.workbench.agile.project.model.ProjectStatus.DESTROYING)
      service.markDestroying(tenantId, projectId, actorId, "cleanup").status shouldBe
        one.ztd.workbench.agile.project.model.ProjectStatus.DESTROYING
      coEvery {
        repository.updateStatus(
          tenantId,
          projectId,
          one.ztd.workbench.agile.project.model.ProjectStatus.ACTIVE,
        )
      } returns true
      service.restoreStatus(
        tenantId,
        projectId,
        one.ztd.workbench.agile.project.model.ProjectStatus.ACTIVE,
      ) shouldBe true
      val updateCommand =
        one.ztd.workbench.agile.project.model.UpdateProjectCommand(
          tenantId = tenantId,
          projectId = projectId,
          name = "Renamed",
          identifier = null,
          description = null,
          nonMemberVisibility = null,
          nonMemberJoinPolicy = null,
          updatedBy = actorId,
        )
      coEvery { repository.update(updateCommand) } returns record.copy(name = "Renamed")
      service.update(updateCommand).name shouldBe "Renamed"
    }

    "requestDestroy delegates to the project repository port" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val record =
        ProjectRecord(
          id = projectId,
          apiId = PublicId.new("prj"),
          tenantId = tenantId,
          identifier = "CORE",
          name = "Core",
          description = null,
          status = one.ztd.workbench.agile.project.model.ProjectStatus.DESTROYING,
        )
      val request =
        ProjectDestroyRequest(
          tenantId = tenantId,
          projectId = projectId,
          deletedBy = actorId,
          deleteReason = "cleanup",
          projectApiId = record.apiId.value,
          tenantApiId = "ten_1",
          payload =
            ProjectDestroyRequestedEvent(
              tenantId = tenantId.toString(),
              projectId = record.apiId.value,
              requestedBy = actorId.toString(),
              deleteReason = "cleanup",
              requestedAt = "2026-07-10T00:00:00Z",
            ),
        )
      val repository = mockk<ProjectRepository>()
      coEvery { repository.requestDestroy(request) } returns record

      ProjectService(repository, mockk(relaxed = true)).requestDestroy(request) shouldBe record
      coVerify(exactly = 1) { repository.requestDestroy(request) }
    }
  })
