package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.project.ProjectService
import ink.doa.workbench.agile.workitem.IssueSubtypeConstraintService
import ink.doa.workbench.core.common.context.TenantRequestContext
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.model.CreateIssueSubtypeConstraintCommand
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/work-item-catalog/type-constraints")
@Tag(name = "Work Item Configuration", description = "Tenant-scoped work item catalog management.")
@SessionSecured
@StandardErrorResponses
class WorkItemSubtypeConstraintController(
  private val constraints: IssueSubtypeConstraintService,
  private val projects: ProjectService,
) {
  @GetMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "workitem.config.read", resource = "workitem.config")
  @Operation(summary = "List work item subtype constraints")
  suspend fun list(
    @RequestParam(required = false) projectId: String?,
    tenantContext: TenantRequestContext,
  ): List<IssueSubtypeConstraintResponse> {
    val resolvedProjectId = projectId?.let { projects.get(tenantContext.tenant.id, it).id }
    return constraints
      .list(tenantContext.tenant.id, resolvedProjectId)
      .map(IssueSubtypeConstraintResponse::from)
  }

  @PostMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "workitem.config.manage", resource = "workitem.config")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create work item subtype constraint")
  suspend fun create(
    @Valid @RequestBody request: CreateIssueSubtypeConstraintRequest,
    tenantContext: TenantRequestContext,
  ): IssueSubtypeConstraintResponse {
    val projectId = request.projectId?.let { projects.get(tenantContext.tenant.id, it).id }
    return IssueSubtypeConstraintResponse.from(
      constraints.create(
        CreateIssueSubtypeConstraintCommand(
          tenantId = tenantContext.tenant.id,
          projectId = projectId,
          parentIssueTypeApiId = request.parentIssueTypeId,
          childIssueTypeApiId = request.childIssueTypeId,
          isDefault = request.isDefault ?: false,
          minChildren = request.minChildren,
          maxChildren = request.maxChildren,
          createdBy = actorUserId(tenantContext),
        )
      )
    )
  }

  @PatchMapping("/{constraintId}/deactivate")
  @Authenticated
  @TenantScoped
  @Authorize(action = "workitem.config.manage", resource = "workitem.config")
  @Operation(summary = "Deactivate work item subtype constraint")
  suspend fun deactivate(
    @PathVariable constraintId: String,
    tenantContext: TenantRequestContext,
  ): IssueSubtypeConstraintResponse =
    IssueSubtypeConstraintResponse.from(
      constraints.deactivate(
        tenantContext.tenant.id,
        parseUuid(constraintId),
        actorUserId(tenantContext),
      )
    )

  private fun actorUserId(tenantContext: TenantRequestContext) =
    tenantContext.actor?.id
      ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)

  private fun parseUuid(value: String): UUID =
    runCatching { UUID.fromString(value) }
      .getOrElse { throw InvalidRequestException(WorkbenchErrorCode.REQUEST_INVALID) }
}
