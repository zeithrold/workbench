package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.workitem.WorkItemTimelineService
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.ProjectScoped
import ink.doa.workbench.web.api.ResourceId
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import ink.doa.workbench.web.api.context.ProjectRequestContext
import ink.doa.workbench.web.api.http.headersIfNext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/projects/{id}/work-items/{workItemId}/timeline")
@Tag(name = "Project Work Items", description = "Project-scoped work item runtime APIs.")
@SessionSecured
@StandardErrorResponses
class ProjectWorkItemTimelineController(private val timelineService: WorkItemTimelineService) {
  @GetMapping
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.view", resource = "issue")
  @Operation(summary = "List work item timeline entries")
  suspend fun list(
    @PathVariable @ResourceId workItemId: String,
    @RequestParam(defaultValue = "50") limit: Int,
    @RequestParam(required = false) cursor: String?,
    projectContext: ProjectRequestContext,
  ): ResponseEntity<List<WorkItemTimelineEntryResponse>> {
    val page =
      timelineService.list(
        projectContext.tenant.id,
        projectContext.project.id,
        workItemId,
        limit,
        cursor,
      )
    return ResponseEntity.ok().headersIfNext(page.nextCursor, WorkItemTimelineResponses.from(page))
  }
}
