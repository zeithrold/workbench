package ink.doa.workbench.web.workitem

import com.fasterxml.jackson.databind.ObjectMapper
import ink.doa.workbench.agile.project.ProjectService
import ink.doa.workbench.agile.workitem.CreateIssueTypeAccessRuleCommand
import ink.doa.workbench.agile.workitem.IssueTypeConfigAccessRuleService
import ink.doa.workbench.agile.workitem.IssueTypeConfigService
import ink.doa.workbench.core.common.context.TenantRequestContext
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.core.workitem.access.WorkItemAccessActionType
import ink.doa.workbench.core.workitem.access.WorkItemAccessSubjectType
import ink.doa.workbench.core.workitem.model.CreateIssueTypeConfigCommand
import ink.doa.workbench.core.workitem.model.IssueTypeConfigPropertyInput
import ink.doa.workbench.core.workitem.model.IssueTypeConfigStatusInput
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
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/work-item-catalog/type-configs")
@Tag(name = "Work Item Configuration", description = "Tenant-scoped work item catalog management.")
@SessionSecured
@StandardErrorResponses
class WorkItemTypeConfigController(
  private val configs: IssueTypeConfigService,
  private val accessRules: IssueTypeConfigAccessRuleService,
  private val projects: ProjectService,
) {
  private val objectMapper = ObjectMapper()

  @GetMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "workitem.config.read", resource = "workitem.config")
  @Operation(summary = "List work item type configs")
  suspend fun list(tenantContext: TenantRequestContext): List<IssueTypeConfigResponse> =
    configs.list(tenantContext.tenant.id).map(IssueTypeConfigResponse::from)

  @PostMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "workitem.config.manage", resource = "workitem.config")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create work item type config")
  suspend fun create(
    @Valid @RequestBody request: CreateIssueTypeConfigRequest,
    tenantContext: TenantRequestContext,
  ): IssueTypeConfigResponse {
    val scope = WorkItemConfigScope.fromDbValue(request.scope)
    val projectId = request.projectId?.let { projects.get(tenantContext.tenant.id, it).id }
    return IssueTypeConfigResponse.from(
      configs.create(
        CreateIssueTypeConfigCommand(
          tenantId = tenantContext.tenant.id,
          scope = scope,
          projectId = projectId,
          issueTypeApiId = request.issueTypeId,
          workflowApiId = request.workflowId,
          nameOverride = request.nameOverride,
          iconOverride = request.iconOverride,
          colorOverride = request.colorOverride,
          rank = request.rank ?: 100,
          createdBy = actorUserId(tenantContext),
          createFields = request.createFields.toJsonObject(objectMapper),
          statuses =
            request.statuses.map {
              IssueTypeConfigStatusInput(
                statusApiId = it.statusId,
                isInitial = it.isInitial ?: false,
                isTerminal = it.isTerminal ?: false,
                rank = it.rank ?: 100,
              )
            },
          properties =
            request.properties.orEmpty().map {
              IssueTypeConfigPropertyInput(
                propertyApiId = it.propertyId,
                validationOverride = it.validationOverride.toJsonObject(objectMapper),
                rank = it.rank ?: 100,
                displayConfig = it.displayConfig.toJsonObject(objectMapper),
              )
            },
        )
      )
    )
  }

  @GetMapping("/{id}/access-rules")
  @Authenticated
  @TenantScoped
  @Authorize(action = "workitem.config.read", resource = "workitem.config")
  @Operation(summary = "List work item type config access rules")
  suspend fun listAccessRules(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ): List<WorkItemAccessRuleResponse> =
    accessRules.list(tenantContext.tenant.id, id).map(WorkItemAccessRuleResponse::from)

  @PostMapping("/{id}/access-rules")
  @Authenticated
  @TenantScoped
  @Authorize(action = "workitem.config.manage", resource = "workitem.config")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create work item type config access rule")
  suspend fun createAccessRule(
    @PathVariable id: String,
    @Valid @RequestBody request: CreateWorkItemAccessRuleRequest,
    tenantContext: TenantRequestContext,
  ): WorkItemAccessRuleResponse =
    WorkItemAccessRuleResponse.from(
      accessRules.create(
        CreateIssueTypeAccessRuleCommand(
          tenantId = tenantContext.tenant.id,
          configApiId = id,
          subjectType = WorkItemAccessSubjectType.fromDbValue(request.subjectType),
          subjectUserId = request.subjectUserId,
          subjectGroupId = request.subjectGroupId,
          subjectRoleCode = request.subjectRoleCode,
          actionType = WorkItemAccessActionType.fromDbValue(request.actionType),
          transitionId = request.transitionId,
          fieldKey = request.fieldKey,
          effect = PermissionEffect.valueOf(request.effect.uppercase()),
          condition = request.condition.toJsonObject(objectMapper),
          rank = request.rank ?: 100,
        )
      )
    )

  @DeleteMapping("/{id}/access-rules/{ruleId}")
  @Authenticated
  @TenantScoped
  @Authorize(action = "workitem.config.manage", resource = "workitem.config")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Deactivate work item type config access rule")
  suspend fun deactivateAccessRule(
    @PathVariable id: String,
    @PathVariable ruleId: String,
    tenantContext: TenantRequestContext,
  ) {
    accessRules.deactivate(tenantContext.tenant.id, id, ruleId)
  }
}
