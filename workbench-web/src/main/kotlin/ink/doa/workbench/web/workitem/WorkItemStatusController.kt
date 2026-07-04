package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.workitem.WorkItemCatalogService
import ink.doa.workbench.core.common.context.TenantRequestContext
import ink.doa.workbench.core.workitem.model.CreateIssueStatusCommand
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
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
@RequestMapping("/api/work-item/statuses")
@Tag(name = "Work Item Configuration", description = "Tenant-scoped work item catalog management.")
@SessionSecured
@StandardErrorResponses
class WorkItemStatusController(private val catalog: WorkItemCatalogService) {
  @GetMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "work_item_config.read", resource = "work_item_config")
  @Operation(summary = "List work item statuses")
  suspend fun list(tenantContext: TenantRequestContext): List<IssueStatusResponse> =
    catalog.listStatuses(tenantContext.tenant.id).map(IssueStatusResponse::from)

  @PostMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "work_item_config.manage", resource = "work_item_config")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create work item status")
  suspend fun create(
    @Valid @RequestBody request: CreateIssueStatusRequest,
    tenantContext: TenantRequestContext,
  ): IssueStatusResponse =
    IssueStatusResponse.from(
      catalog.createStatus(
        CreateIssueStatusCommand(
          tenantId = tenantContext.tenant.id,
          code = request.code,
          name = request.name,
          statusGroup = WorkItemStatusGroup.fromDbValue(request.statusGroup),
          rank = request.rank ?: 100,
          color = request.color,
          isTerminal = request.isTerminal ?: false,
        )
      )
    )

  @PatchMapping("/{statusId}/deactivate")
  @Authenticated
  @TenantScoped
  @Authorize(action = "work_item_config.manage", resource = "work_item_config")
  @Operation(summary = "Deactivate work item status")
  suspend fun deactivate(
    @PathVariable statusId: String,
    tenantContext: TenantRequestContext,
  ): IssueStatusResponse =
    IssueStatusResponse.from(
      catalog.deactivateStatus(
        tenantContext.tenant.id,
        statusId,
        actorUserId(tenantContext),
      )
    )
}
