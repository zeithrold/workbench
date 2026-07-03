package doa.ink.workbench.agile.project

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.core.project.model.CreateProjectCommand
import doa.ink.workbench.core.project.model.ProjectRecord
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID

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
  })
