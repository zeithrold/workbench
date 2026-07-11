package ink.doa.workbench.jobs.project

import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.project.ProjectRepository
import org.springframework.stereotype.Component

@ink.doa.workbench.jobs.JobsEnabled
@Component
class ProjectDestroyLookupSupport(
  val tenants: TenantRepository,
  val projects: ProjectRepository,
  val users: UserRepository,
)
