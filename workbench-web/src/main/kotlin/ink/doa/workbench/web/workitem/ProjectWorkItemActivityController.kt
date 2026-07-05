package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.workitem.WorkItemActivityService
import ink.doa.workbench.core.common.context.ProjectRequestContext
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.ProjectScoped
import ink.doa.workbench.web.api.ResourceId
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import java.time.OffsetDateTime
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/projects/{id}/work-items/{workItemId}/activities")
@Tag(name = "Project Work Items", description = "Project-scoped work item runtime APIs.")
@SessionSecured
@StandardErrorResponses
class ProjectWorkItemActivityController(private val activityService: WorkItemActivityService) {
  @GetMapping
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.view", resource = "issue")
  @Operation(summary = "List work item activities")
  suspend fun list(
    @PathVariable @ResourceId workItemId: String,
    @RequestParam(defaultValue = "50") limit: Int,
    @RequestParam(required = false) before: OffsetDateTime?,
    projectContext: ProjectRequestContext,
  ): WorkItemActivityListResponse =
    WorkItemActivityListResponse.from(
      activityService.list(
        projectContext.tenant.id,
        projectContext.project.id,
        workItemId,
        limit,
        before,
      )
    )
}
