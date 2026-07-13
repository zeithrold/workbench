package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.workitem.WorkflowConfigurationService
import ink.doa.workbench.agile.workitem.model.CreateWorkflowCommand
import ink.doa.workbench.agile.workitem.model.CreateWorkflowTransitionCommand
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import ink.doa.workbench.web.api.context.TenantRequestContext
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
@RequestMapping("/api/work-item-catalog/workflows")
@Tag(name = "Workflows", description = "Tenant-scoped workflow management.")
@SessionSecured
@StandardErrorResponses
class WorkflowController(private val workflows: WorkflowConfigurationService) {
  @GetMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "workitem.config.read", resource = "workitem.config")
  @Operation(summary = "List workflows")
  suspend fun list(tenantContext: TenantRequestContext): List<WorkflowResponse> =
    workflows.listWorkflows(tenantContext.tenant.id).map(WorkflowResponse::from)

  @PostMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "workflow.manage", resource = "workflow")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create workflow")
  suspend fun create(
    @Valid @RequestBody request: CreateWorkflowRequest,
    tenantContext: TenantRequestContext,
  ): WorkflowResponse =
    WorkflowResponse.from(
      workflows.createWorkflow(
        CreateWorkflowCommand(
          tenantId = tenantContext.tenant.id,
          code = request.code,
          name = request.name,
          description = request.description,
          createdBy = actorUserId(tenantContext),
        )
      )
    )

  @PostMapping("/{id}/publish")
  @Authenticated
  @TenantScoped
  @Authorize(action = "workflow.manage", resource = "workflow")
  @Operation(summary = "Publish workflow")
  suspend fun publish(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ): WorkflowResponse =
    WorkflowResponse.from(workflows.publishWorkflow(tenantContext.tenant.id, id))

  @PatchMapping("/{id}/deactivate")
  @Authenticated
  @TenantScoped
  @Authorize(action = "workflow.manage", resource = "workflow")
  @Operation(summary = "Deactivate workflow")
  suspend fun deactivate(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ): WorkflowResponse =
    WorkflowResponse.from(
      workflows.deactivateWorkflow(
        tenantContext.tenant.id,
        id,
        actorUserId(tenantContext),
      )
    )

  @GetMapping("/{id}/transitions")
  @Authenticated
  @TenantScoped
  @Authorize(action = "workitem.config.read", resource = "workitem.config")
  @Operation(summary = "List workflow transitions")
  suspend fun listTransitions(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ): List<WorkflowTransitionResponse> =
    workflows.listTransitions(tenantContext.tenant.id, id).map(WorkflowTransitionResponse::from)

  @PostMapping("/{id}/transitions")
  @Authenticated
  @TenantScoped
  @Authorize(action = "workflow.manage", resource = "workflow")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create workflow transition")
  suspend fun createTransition(
    @PathVariable id: String,
    @Valid @RequestBody request: CreateWorkflowTransitionRequest,
    tenantContext: TenantRequestContext,
  ): WorkflowTransitionResponse =
    WorkflowTransitionResponse.from(
      workflows.createTransition(
        CreateWorkflowTransitionCommand(
          tenantId = tenantContext.tenant.id,
          workflowApiId = id,
          name = request.name,
          fromStatusApiId = request.fromStatusId,
          toStatusApiId = request.toStatusId,
          rank = request.rank ?: 100,
          preconditionAst = request.preconditionAst.toJsonObject(),
          fields = request.fields.toJsonObject(),
        )
      )
    )

  @PatchMapping("/{id}/transitions/{transitionId}/deactivate")
  @Authenticated
  @TenantScoped
  @Authorize(action = "workflow.manage", resource = "workflow")
  @Operation(summary = "Deactivate workflow transition")
  suspend fun deactivateTransition(
    @PathVariable id: String,
    @PathVariable transitionId: String,
    tenantContext: TenantRequestContext,
  ): WorkflowTransitionResponse =
    WorkflowTransitionResponse.from(
      workflows.deactivateTransition(
        tenantId = tenantContext.tenant.id,
        workflowApiIdOrCode = id,
        transitionApiId = transitionId,
      )
    )
}
