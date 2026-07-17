package one.ztd.workbench.web.workitem

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import one.ztd.workbench.agile.project.ProjectService
import one.ztd.workbench.agile.workitem.WorkItemCatalogService
import one.ztd.workbench.agile.workitem.model.CreateIssueTypeCommand
import one.ztd.workbench.agile.workitem.model.WorkItemConfigScope
import one.ztd.workbench.web.api.Authenticated
import one.ztd.workbench.web.api.Authorize
import one.ztd.workbench.web.api.SessionSecured
import one.ztd.workbench.web.api.StandardErrorResponses
import one.ztd.workbench.web.api.TenantScoped
import one.ztd.workbench.web.api.context.TenantRequestContext
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/work-item-catalog/types")
@Tag(name = "Work Item Configuration", description = "Tenant-scoped work item catalog management.")
@SessionSecured
@StandardErrorResponses
class WorkItemIssueTypeController(
  private val catalog: WorkItemCatalogService,
  private val projects: ProjectService,
) {
  @GetMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "workitem.config.read", resource = "workitem.config")
  @Operation(summary = "List work item types")
  suspend fun list(tenantContext: TenantRequestContext): List<IssueTypeResponse> =
    catalog.listIssueTypes(tenantContext.tenant.id).map(IssueTypeResponse::from)

  @PostMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "workitem.config.manage", resource = "workitem.config")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create work item type")
  suspend fun create(
    @Valid @RequestBody request: CreateIssueTypeRequest,
    tenantContext: TenantRequestContext,
  ): IssueTypeResponse {
    val scope = WorkItemConfigScope.fromDbValue(request.scope)
    val projectId = request.projectId?.let { projects.get(tenantContext.tenant.id, it).id }
    return IssueTypeResponse.from(
      catalog.createIssueType(
        CreateIssueTypeCommand(
          tenantId = tenantContext.tenant.id,
          scope = scope,
          projectId = projectId,
          code = request.code,
          name = request.name,
          description = request.description,
          icon = request.icon,
          color = request.color,
          rank = request.rank ?: 100,
        )
      )
    )
  }

  @PatchMapping("/{typeId}/deactivate")
  @Authenticated
  @TenantScoped
  @Authorize(action = "workitem.config.manage", resource = "workitem.config")
  @Operation(summary = "Deactivate work item type")
  suspend fun deactivate(
    @PathVariable typeId: String,
    tenantContext: TenantRequestContext,
  ): IssueTypeResponse =
    IssueTypeResponse.from(
      catalog.deactivateIssueType(
        tenantContext.tenant.id,
        typeId,
        actorUserId(tenantContext),
      )
    )
}
