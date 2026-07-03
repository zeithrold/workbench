package ink.doa.workbench.web.support

import ink.doa.workbench.agile.project.ProjectMemberService
import ink.doa.workbench.agile.project.ProjectOperationalGuard
import ink.doa.workbench.core.project.ProjectRepository
import ink.doa.workbench.service.project.ProjectManagementApplicationService
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class ProjectWebMvcSupport {
  @Bean fun projectRepository(): ProjectRepository = mockk(relaxed = true)

  @Bean
  fun projectOperationalGuard(projects: ProjectRepository): ProjectOperationalGuard =
    ProjectOperationalGuard(projects)

  @Bean fun projectManagementService(): ProjectManagementApplicationService = mockk(relaxed = true)

  @Bean fun projectMemberService(): ProjectMemberService = mockk(relaxed = true)
}
