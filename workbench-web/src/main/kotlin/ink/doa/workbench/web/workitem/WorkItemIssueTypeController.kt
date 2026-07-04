package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.project.ProjectService
import ink.doa.workbench.agile.workitem.WorkItemCatalogService
import ink.doa.workbench.core.common.context.TenantRequestContext
import ink.doa.workbench.core.workitem.model.CreateIssueTypeCommand
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
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
@RequestMapping("/api/work-item/types")
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
