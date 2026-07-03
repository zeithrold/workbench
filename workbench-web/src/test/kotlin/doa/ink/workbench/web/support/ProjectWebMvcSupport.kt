package doa.ink.workbench.web.support

import doa.ink.workbench.agile.project.ProjectMemberService
import doa.ink.workbench.agile.project.ProjectOperationalGuard
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.service.project.ProjectManagementService
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class ProjectWebMvcSupport {
  @Bean fun projectRepository(): ProjectRepository = mockk(relaxed = true)

  @Bean
  fun projectOperationalGuard(projects: ProjectRepository): ProjectOperationalGuard =
    ProjectOperationalGuard(projects)

  @Bean fun projectManagementService(): ProjectManagementService = mockk(relaxed = true)

  @Bean fun projectMemberService(): ProjectMemberService = mockk(relaxed = true)
}
