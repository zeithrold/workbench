package doa.ink.workbench.project

import doa.ink.workbench.project.model.CreateProjectCommand
import doa.ink.workbench.project.model.ProjectRecord
import doa.ink.workbench.shared.ids.PublicId
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

      val result = ProjectService(repository).create(command)

      result shouldBe record
      coVerify(exactly = 1) { repository.create(command) }
    }
  })
