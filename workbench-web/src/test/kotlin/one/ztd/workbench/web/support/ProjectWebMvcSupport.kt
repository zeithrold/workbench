package one.ztd.workbench.web.support

import io.mockk.mockk
import one.ztd.workbench.agile.project.ProjectMemberService
import one.ztd.workbench.agile.project.ProjectOperationalGuard
import one.ztd.workbench.agile.project.ProjectRepository
import one.ztd.workbench.application.project.ProjectManagementApplicationService
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
