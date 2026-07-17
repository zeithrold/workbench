package one.ztd.workbench.web.workitem

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import java.net.URI
import one.ztd.workbench.agile.workitem.WorkItemViewService
import one.ztd.workbench.agile.workitem.view.CreateWorkItemViewCommand
import one.ztd.workbench.agile.workitem.view.DeleteWorkItemViewCommand
import one.ztd.workbench.agile.workitem.view.UpdateWorkItemViewCommand
import one.ztd.workbench.agile.workitem.view.WorkItemViewDefaults
import one.ztd.workbench.web.api.Authenticated
import one.ztd.workbench.web.api.Authorize
import one.ztd.workbench.web.api.ProjectScoped
import one.ztd.workbench.web.api.ResourceId
import one.ztd.workbench.web.api.SessionSecured
import one.ztd.workbench.web.api.StandardErrorResponses
import one.ztd.workbench.web.api.TenantScoped
import one.ztd.workbench.web.api.context.ProjectRequestContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/projects/{id}/work-item-views")
@Tag(name = "Project Work Item Views", description = "Project-scoped work item view APIs.")
@SessionSecured
@StandardErrorResponses
class ProjectWorkItemViewController(private val viewService: WorkItemViewService) {
  @GetMapping
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "view.read", resource = "view")
  @Operation(summary = "List project work item views")
  suspend fun list(projectContext: ProjectRequestContext): List<WorkItemViewResponse> =
    viewService
      .list(projectContext.tenant.id, projectContext.project.id, actorUserId(projectContext))
      .map(WorkItemViewResponse::from)

  @PostMapping
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "view.create", resource = "view")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a project work item view")
  suspend fun create(
    @Valid @RequestBody request: CreateWorkItemViewRequest,
    projectContext: ProjectRequestContext,
  ): ResponseEntity<WorkItemViewResponse> {
    val view =
      viewService.create(
        CreateWorkItemViewCommand(
          tenantId = projectContext.tenant.id,
          projectId = projectContext.project.id,
          ownerId = actorUserId(projectContext),
          name = request.name,
          description = request.description,
          visibility = request.visibilityEnum(),
          queryAst = request.query.toJsonElement(WorkItemViewDefaults.EMPTY_QUERY),
          displayFields =
            request.displayFields.toJsonElement(WorkItemViewDefaults.EMPTY_DISPLAY_FIELDS),
        )
      )
    val response = WorkItemViewResponse.from(view)
    return ResponseEntity.created(
        URI.create(
          "/api/projects/${projectContext.project.publicId.value}/work-item-views/${response.id}"
        )
      )
      .body(response)
  }

  @GetMapping("/{viewId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "view.read", resource = "view")
  @Operation(summary = "Get a project work item view")
  suspend fun get(
    @PathVariable @ResourceId viewId: String,
    projectContext: ProjectRequestContext,
  ): WorkItemViewResponse =
    WorkItemViewResponse.from(
      viewService.get(
        projectContext.tenant.id,
        projectContext.project.id,
        viewId,
        actorUserId(projectContext),
      )
    )

  @PatchMapping("/{viewId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "view.read", resource = "view")
  @Operation(summary = "Update a project work item view")
  suspend fun update(
    @PathVariable @ResourceId viewId: String,
    @Valid @RequestBody request: UpdateWorkItemViewRequest,
    projectContext: ProjectRequestContext,
  ): WorkItemViewResponse =
    WorkItemViewResponse.from(
      viewService.update(
        UpdateWorkItemViewCommand(
          tenantId = projectContext.tenant.id,
          viewApiId = viewId,
          projectId = projectContext.project.id,
          actorUserId = actorUserId(projectContext),
          name = request.name,
          description = request.description,
          visibility = request.visibilityEnum(),
          queryAst = request.query?.toJsonElement(),
          displayFields = request.displayFields?.toJsonElement(),
        )
      )
    )

  @DeleteMapping("/{viewId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "view.read", resource = "view")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a project work item view")
  suspend fun delete(
    @PathVariable @ResourceId viewId: String,
    projectContext: ProjectRequestContext,
  ) {
    viewService.delete(
      DeleteWorkItemViewCommand(
        tenantId = projectContext.tenant.id,
        viewApiId = viewId,
        projectId = projectContext.project.id,
        actorUserId = actorUserId(projectContext),
      )
    )
  }
}
