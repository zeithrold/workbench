package ink.doa.workbench.worker.project

import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.project.ProjectRepository
import org.springframework.stereotype.Component

@Component
class ProjectDestroyLookupSupport(
  val tenants: TenantRepository,
  val projects: ProjectRepository,
  val users: UserRepository,
)
