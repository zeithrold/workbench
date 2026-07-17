package one.ztd.workbench.application.project

import one.ztd.workbench.agile.project.ProjectAccessService
import one.ztd.workbench.agile.project.ProjectService
import one.ztd.workbench.application.permission.PermissionBootstrapService
import one.ztd.workbench.identity.UserLookupService
import org.springframework.stereotype.Component

@Component
class ProjectManagementDependencies(
  val projects: ProjectService,
  val userLookupService: UserLookupService,
  val projectAccess: ProjectAccessService,
  val permissionBootstrap: PermissionBootstrapService,
  val infrastructure: ProjectManagementInfrastructure,
)
