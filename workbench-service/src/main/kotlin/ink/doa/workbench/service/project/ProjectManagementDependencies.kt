package ink.doa.workbench.service.project

import ink.doa.workbench.agile.project.ProjectAccessService
import ink.doa.workbench.agile.project.ProjectService
import ink.doa.workbench.security.identity.UserLookupService
import ink.doa.workbench.security.permission.PermissionBootstrapService
import org.springframework.stereotype.Component

@Component
class ProjectManagementDependencies(
  val projects: ProjectService,
  val userLookupService: UserLookupService,
  val projectAccess: ProjectAccessService,
  val permissionBootstrap: PermissionBootstrapService,
  val infrastructure: ProjectManagementInfrastructure,
)
