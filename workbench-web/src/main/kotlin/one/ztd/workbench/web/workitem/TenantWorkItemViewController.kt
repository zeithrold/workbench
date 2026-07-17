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
import one.ztd.workbench.web.api.ResourceId
import one.ztd.workbench.web.api.SessionSecured
import one.ztd.workbench.web.api.StandardErrorResponses
import one.ztd.workbench.web.api.TenantScoped
import one.ztd.workbench.web.api.context.TenantRequestContext
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
@RequestMapping("/api/work-item-views")
@Tag(name = "Work Item Views", description = "Tenant-scoped work item view APIs.")
@SessionSecured
@StandardErrorResponses
class TenantWorkItemViewController(private val viewService: WorkItemViewService) {
  @GetMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "view.read", resource = "view")
  @Operation(summary = "List tenant work item views")
  suspend fun list(tenantContext: TenantRequestContext): List<WorkItemViewResponse> =
    viewService
      .list(tenantContext.tenant.id, projectId = null, actorUserId(tenantContext))
      .map(WorkItemViewResponse::from)

  @PostMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "view.create", resource = "view")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a tenant work item view")
  suspend fun create(
    @Valid @RequestBody request: CreateWorkItemViewRequest,
    tenantContext: TenantRequestContext,
  ): ResponseEntity<WorkItemViewResponse> {
    val view =
      viewService.create(
        CreateWorkItemViewCommand(
          tenantId = tenantContext.tenant.id,
          projectId = null,
          ownerId = actorUserId(tenantContext),
          name = request.name,
          description = request.description,
          visibility = request.visibilityEnum(),
          queryAst = request.query.toJsonElement(WorkItemViewDefaults.EMPTY_QUERY),
          displayFields =
            request.displayFields.toJsonElement(WorkItemViewDefaults.EMPTY_DISPLAY_FIELDS),
        )
      )
    val response = WorkItemViewResponse.from(view)
    return ResponseEntity.created(URI.create("/api/work-item-views/${response.id}")).body(response)
  }

  @GetMapping("/{viewId}")
  @Authenticated
  @TenantScoped
  @Authorize(action = "view.read", resource = "view")
  @Operation(summary = "Get a tenant work item view")
  suspend fun get(
    @PathVariable @ResourceId viewId: String,
    tenantContext: TenantRequestContext,
  ): WorkItemViewResponse =
    WorkItemViewResponse.from(
      viewService.get(tenantContext.tenant.id, projectId = null, viewId, actorUserId(tenantContext))
    )

  @PatchMapping("/{viewId}")
  @Authenticated
  @TenantScoped
  @Authorize(action = "view.read", resource = "view")
  @Operation(summary = "Update a tenant work item view")
  suspend fun update(
    @PathVariable @ResourceId viewId: String,
    @Valid @RequestBody request: UpdateWorkItemViewRequest,
    tenantContext: TenantRequestContext,
  ): WorkItemViewResponse =
    WorkItemViewResponse.from(
      viewService.update(
        UpdateWorkItemViewCommand(
          tenantId = tenantContext.tenant.id,
          viewApiId = viewId,
          projectId = null,
          actorUserId = actorUserId(tenantContext),
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
  @Authorize(action = "view.read", resource = "view")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a tenant work item view")
  suspend fun delete(
    @PathVariable @ResourceId viewId: String,
    tenantContext: TenantRequestContext,
  ) {
    viewService.delete(
      DeleteWorkItemViewCommand(
        tenantId = tenantContext.tenant.id,
        viewApiId = viewId,
        projectId = null,
        actorUserId = actorUserId(tenantContext),
      )
    )
  }
}
