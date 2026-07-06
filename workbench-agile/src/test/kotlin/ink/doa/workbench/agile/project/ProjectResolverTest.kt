package ink.doa.workbench.agile.project

import ink.doa.workbench.agile.testfixtures.AgileProjectFixtures
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.project.ProjectRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID

class ProjectResolverTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val record = AgileProjectFixtures.sampleProject(tenantId = tenantId)

    "resolveProject returns project when repository finds it" {
      val repository = mockk<ProjectRepository>()
      coEvery { repository.findByApiId(tenantId, record.apiId.value) } returns record

      ProjectResolver(repository).resolveProject(tenantId, record.apiId.value) shouldBe record
    }

    "resolveProject throws when project is missing" {
      val repository = mockk<ProjectRepository>()
      coEvery { repository.findByApiId(tenantId, "missing") } returns null

      shouldThrow<ResourceNotFoundException> {
          ProjectResolver(repository).resolveProject(tenantId, "missing")
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND
    }
  })
