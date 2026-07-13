package ink.doa.workbench.application.jobs.project

import ink.doa.workbench.agile.project.ProjectRepository
import ink.doa.workbench.identity.UserRepository
import ink.doa.workbench.tenant.TenantRepository
import org.springframework.stereotype.Component

@ink.doa.workbench.application.jobs.JobsEnabled
@Component
class ProjectDestroyLookupSupport(
  val tenants: TenantRepository,
  val projects: ProjectRepository,
  val users: UserRepository,
)
