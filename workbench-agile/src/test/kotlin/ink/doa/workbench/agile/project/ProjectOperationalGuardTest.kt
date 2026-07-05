package ink.doa.workbench.agile.project

import ink.doa.workbench.core.common.errors.ProjectArchivedException
import ink.doa.workbench.core.common.errors.ProjectDestroyingException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.project.ProjectRepository
import ink.doa.workbench.core.project.model.ProjectRecord
import ink.doa.workbench.core.project.model.ProjectStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID

class ProjectOperationalGuardTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    fun project(status: ProjectStatus) =
      ProjectRecord(
        id = projectId,
        apiId = PublicId.new("prj"),
        tenantId = tenantId,
        identifier = "demo",
        name = "Demo",
        description = null,
        status = status,
        createdBy = UUID.randomUUID(),
      )

    "ensureOperational throws when project is missing" {
      val projects = mockk<ProjectRepository>()
      coEvery { projects.findById(tenantId, projectId) } returns null
      val guard = ProjectOperationalGuard(projects)

      shouldThrow<ResourceNotFoundException> { guard.ensureOperational(tenantId, projectId) }
    }

    "ensureOperational throws when project is destroying" {
      val projects = mockk<ProjectRepository>()
      coEvery { projects.findById(tenantId, projectId) } returns project(ProjectStatus.DESTROYING)
      val guard = ProjectOperationalGuard(projects)

      shouldThrow<ProjectDestroyingException> { guard.ensureOperational(tenantId, projectId) }
    }

    "ensureWritable throws when project is archived" {
      val projects = mockk<ProjectRepository>()
      coEvery { projects.findById(tenantId, projectId) } returns project(ProjectStatus.ARCHIVED)
      val guard = ProjectOperationalGuard(projects)

      shouldThrow<ProjectArchivedException> { guard.ensureWritable(tenantId, projectId) }
    }

    "ensureWritable returns active project" {
      val projects = mockk<ProjectRepository>()
      val active = project(ProjectStatus.ACTIVE)
      coEvery { projects.findById(tenantId, projectId) } returns active
      val guard = ProjectOperationalGuard(projects)

      guard.ensureWritable(tenantId, projectId).identifier shouldBe "demo"
    }
  })
