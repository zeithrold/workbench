package ink.doa.workbench.application.project

import ink.doa.workbench.agile.project.ProjectAccessService
import ink.doa.workbench.agile.project.ProjectService
import ink.doa.workbench.application.permission.PermissionBootstrapService
import ink.doa.workbench.identity.UserLookupService
import org.springframework.stereotype.Component

@Component
class ProjectManagementDependencies(
  val projects: ProjectService,
  val userLookupService: UserLookupService,
  val projectAccess: ProjectAccessService,
  val permissionBootstrap: PermissionBootstrapService,
  val infrastructure: ProjectManagementInfrastructure,
)
