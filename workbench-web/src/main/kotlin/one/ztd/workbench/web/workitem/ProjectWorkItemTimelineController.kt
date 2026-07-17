package one.ztd.workbench.web.workitem

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import one.ztd.workbench.agile.workitem.WorkItemTimelineService
import one.ztd.workbench.web.api.Authenticated
import one.ztd.workbench.web.api.Authorize
import one.ztd.workbench.web.api.ProjectScoped
import one.ztd.workbench.web.api.ResourceId
import one.ztd.workbench.web.api.SessionSecured
import one.ztd.workbench.web.api.StandardErrorResponses
import one.ztd.workbench.web.api.TenantScoped
import one.ztd.workbench.web.api.context.ProjectRequestContext
import one.ztd.workbench.web.api.http.headersIfNext
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
