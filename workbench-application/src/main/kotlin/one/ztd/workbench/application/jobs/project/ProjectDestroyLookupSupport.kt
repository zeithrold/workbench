package one.ztd.workbench.application.jobs.project

import one.ztd.workbench.agile.project.ProjectRepository
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.tenant.TenantRepository
import org.springframework.stereotype.Component

@one.ztd.workbench.application.jobs.JobsEnabled
@Component
class ProjectDestroyLookupSupport(
  val tenants: TenantRepository,
  val projects: ProjectRepository,
  val users: UserRepository,
)
