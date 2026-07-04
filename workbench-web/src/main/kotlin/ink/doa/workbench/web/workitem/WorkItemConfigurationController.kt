package ink.doa.workbench.web.workitem

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import ink.doa.workbench.agile.project.ProjectService
import ink.doa.workbench.agile.workitem.IssueTypeConfigService
import ink.doa.workbench.agile.workitem.WorkItemCatalogService
import ink.doa.workbench.agile.workitem.WorkflowConfigurationService
import ink.doa.workbench.core.common.context.ProjectRequestContext
import ink.doa.workbench.core.common.context.TenantRequestContext
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.model.CreateIssueStatusCommand
import ink.doa.workbench.core.workitem.model.CreateIssueTypeCommand
import ink.doa.workbench.core.workitem.model.CreateIssueTypeConfigCommand
import ink.doa.workbench.core.workitem.model.CreatePropertyDefinitionCommand
import ink.doa.workbench.core.workitem.model.CreateWorkflowCommand
import ink.doa.workbench.core.workitem.model.CreateWorkflowTransitionCommand
import ink.doa.workbench.core.workitem.model.EffectiveIssueTypeConfig
import ink.doa.workbench.core.workitem.model.IssueStatusRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigPropertyInput
import ink.doa.workbench.core.workitem.model.IssueTypeConfigPropertyRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigStatusInput
import ink.doa.workbench.core.workitem.model.IssueTypeConfigStatusRecord
import ink.doa.workbench.core.workitem.model.IssueTypeRecord
import ink.doa.workbench.core.workitem.model.PropertyDefinitionRecord
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.core.workitem.model.WorkflowRecord
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.ProjectScoped
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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
@RequestMapping("/api/work-item")
@Tag(name = "Work Item Configuration", description = "Tenant-scoped work item catalog management.")
@SessionSecured
@StandardErrorResponses
@Suppress("TooManyFunctions")
class WorkItemConfigurationController(
  private val catalog: WorkItemCatalogService,
  private val configs: IssueTypeConfigService,
  private val projects: ProjectService,
) {
  private val objectMapper = ObjectMapper()

  @GetMapping("/statuses")
  @Authenticated
  @TenantScoped
  @Authorize(action = "work_item_config.read", resource = "work_item_config")
  @Operation(summary = "List work item statuses")
  suspend fun listStatuses(tenantContext: TenantRequestContext): List<IssueStatusResponse> =
    catalog.listStatuses(tenantContext.tenant.id).map(IssueStatusResponse::from)

  @PostMapping("/statuses")
  @Authenticated
  @TenantScoped
  @Authorize(action = "work_item_config.manage", resource = "work_item_config")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create work item status")
  suspend fun createStatus(
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

  @PatchMapping("/statuses/{statusId}/deactivate")
  @Authenticated
  @TenantScoped
  @Authorize(action = "work_item_config.manage", resource = "work_item_config")
  @Operation(summary = "Deactivate work item status")
  suspend fun deactivateStatus(
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

  @GetMapping("/properties")
  @Authenticated
  @TenantScoped
  @Authorize(action = "work_item_config.read", resource = "work_item_config")
  @Operation(summary = "List work item properties")
  suspend fun listProperties(
    tenantContext: TenantRequestContext
  ): List<PropertyDefinitionResponse> =
    catalog.listProperties(tenantContext.tenant.id).map(PropertyDefinitionResponse::from)

  @PostMapping("/properties")
  @Authenticated
  @TenantScoped
  @Authorize(action = "work_item_config.manage", resource = "work_item_config")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create work item property")
  suspend fun createProperty(
    @Valid @RequestBody request: CreatePropertyDefinitionRequest,
    tenantContext: TenantRequestContext,
  ): PropertyDefinitionResponse =
    PropertyDefinitionResponse.from(
      catalog.createProperty(
        CreatePropertyDefinitionCommand(
          tenantId = tenantContext.tenant.id,
          code = request.code,
          name = request.name,
          description = request.description,
          dataType = WorkItemPropertyDataType.fromDbValue(request.dataType),
          isArray = request.isArray ?: false,
          validationSchema = request.validationSchema.toJsonObject(objectMapper),
          searchConfig = request.searchConfig.toJsonObject(objectMapper),
        )
      )
    )

  @PatchMapping("/properties/{propertyId}/deactivate")
  @Authenticated
  @TenantScoped
  @Authorize(action = "work_item_config.manage", resource = "work_item_config")
  @Operation(summary = "Deactivate work item property")
  suspend fun deactivateProperty(
    @PathVariable propertyId: String,
    tenantContext: TenantRequestContext,
  ): PropertyDefinitionResponse =
    PropertyDefinitionResponse.from(
      catalog.deactivateProperty(
        tenantContext.tenant.id,
        propertyId,
        actorUserId(tenantContext),
      )
    )

  @GetMapping("/types")
  @Authenticated
  @TenantScoped
  @Authorize(action = "work_item_config.read", resource = "work_item_config")
  @Operation(summary = "List work item types")
  suspend fun listTypes(tenantContext: TenantRequestContext): List<IssueTypeResponse> =
    catalog.listIssueTypes(tenantContext.tenant.id).map(IssueTypeResponse::from)

  @PostMapping("/types")
  @Authenticated
  @TenantScoped
  @Authorize(action = "work_item_config.manage", resource = "work_item_config")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create work item type")
  suspend fun createType(
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

  @PatchMapping("/types/{typeId}/deactivate")
  @Authenticated
  @TenantScoped
  @Authorize(action = "work_item_config.manage", resource = "work_item_config")
  @Operation(summary = "Deactivate work item type")
  suspend fun deactivateType(
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

  @GetMapping("/type-configs")
  @Authenticated
  @TenantScoped
  @Authorize(action = "work_item_config.read", resource = "work_item_config")
  @Operation(summary = "List work item type configs")
  suspend fun listTypeConfigs(tenantContext: TenantRequestContext): List<IssueTypeConfigResponse> =
    configs.list(tenantContext.tenant.id).map(IssueTypeConfigResponse::from)

  @PostMapping("/type-configs")
  @Authenticated
  @TenantScoped
  @Authorize(action = "work_item_config.manage", resource = "work_item_config")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create work item type config")
  suspend fun createTypeConfig(
    @Valid @RequestBody request: CreateIssueTypeConfigRequest,
    tenantContext: TenantRequestContext,
  ): IssueTypeConfigResponse {
    val scope = WorkItemConfigScope.fromDbValue(request.scope)
    val projectId = request.projectId?.let { projects.get(tenantContext.tenant.id, it).id }
    val actorUserId =
      tenantContext.actor?.id
        ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
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
          createdBy = actorUserId,
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

  private fun actorUserId(tenantContext: TenantRequestContext) =
    tenantContext.actor?.id
      ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
}

@RestController
@RequestMapping("/api/workflows")
@Tag(name = "Workflows", description = "Tenant-scoped workflow management.")
@SessionSecured
@StandardErrorResponses
class WorkflowController(private val workflows: WorkflowConfigurationService) {
  @GetMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "work_item_config.read", resource = "work_item_config")
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
  ): WorkflowResponse {
    val actorUserId =
      tenantContext.actor?.id
        ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
    return WorkflowResponse.from(
      workflows.createWorkflow(
        CreateWorkflowCommand(
          tenantId = tenantContext.tenant.id,
          code = request.code,
          name = request.name,
          description = request.description,
          createdBy = actorUserId,
        )
      )
    )
  }

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
  @Authorize(action = "work_item_config.read", resource = "work_item_config")
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
          permissionCondition = request.permissionCondition.toJsonObject(),
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

  private fun actorUserId(tenantContext: TenantRequestContext) =
    tenantContext.actor?.id
      ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
}

@RestController
@RequestMapping("/api/projects/{id}/work-item/type-configs")
@Tag(name = "Project Work Item Configuration")
@SessionSecured
@StandardErrorResponses
class ProjectWorkItemConfigurationController(private val configs: IssueTypeConfigService) {
  @GetMapping("/effective/{issueTypeId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "work_item_config.read", resource = "work_item_config")
  @Operation(summary = "Resolve effective work item type config")
  suspend fun effective(
    @PathVariable issueTypeId: String,
    projectContext: ProjectRequestContext,
  ): EffectiveIssueTypeConfigResponse =
    EffectiveIssueTypeConfigResponse.from(
      configs.resolveEffective(
        tenantId = projectContext.tenant.id,
        projectId = projectContext.project.id,
        issueTypeApiIdOrCode = issueTypeId,
      )
    )
}

data class CreateIssueStatusRequest(
  @field:NotBlank val code: String,
  @field:NotBlank val name: String,
  @field:NotBlank val statusGroup: String,
  val rank: Int? = null,
  val color: String? = null,
  val isTerminal: Boolean? = null,
)

data class CreatePropertyDefinitionRequest(
  @field:NotBlank val code: String,
  @field:NotBlank val name: String,
  @field:NotBlank val dataType: String,
  val description: String? = null,
  val isArray: Boolean? = null,
  val validationSchema: JsonNode? = null,
  val searchConfig: JsonNode? = null,
)

data class CreateIssueTypeRequest(
  @field:NotBlank val scope: String,
  val projectId: String? = null,
  @field:NotBlank val code: String,
  @field:NotBlank val name: String,
  val description: String? = null,
  val icon: String? = null,
  val color: String? = null,
  val rank: Int? = null,
)

data class CreateWorkflowRequest(
  @field:NotBlank val code: String,
  @field:NotBlank val name: String,
  val description: String? = null,
)

data class CreateWorkflowTransitionRequest(
  @field:NotBlank val name: String,
  val fromStatusId: String? = null,
  @field:NotBlank val toStatusId: String,
  val rank: Int? = null,
  val permissionCondition: JsonNode? = null,
  val preconditionAst: JsonNode? = null,
  val fields: JsonNode? = null,
)

data class CreateIssueTypeConfigRequest(
  @field:NotBlank val scope: String,
  val projectId: String? = null,
  @field:NotBlank val issueTypeId: String,
  @field:NotBlank val workflowId: String,
  val nameOverride: String? = null,
  val iconOverride: String? = null,
  val colorOverride: String? = null,
  val rank: Int? = null,
  val createFields: JsonNode,
  val statuses: List<TypeConfigStatusRequest>,
  val properties: List<TypeConfigPropertyRequest>? = null,
)

data class TypeConfigStatusRequest(
  @field:NotBlank val statusId: String,
  val isInitial: Boolean? = null,
  val isTerminal: Boolean? = null,
  val rank: Int? = null,
)

data class TypeConfigPropertyRequest(
  @field:NotBlank val propertyId: String,
  val validationOverride: JsonNode? = null,
  val rank: Int? = null,
  val displayConfig: JsonNode? = null,
)

data class IssueStatusResponse(
  val id: String,
  val code: String,
  val name: String,
  val statusGroup: String,
  val rank: Int,
  val color: String?,
  val isTerminal: Boolean,
) {
  companion object {
    fun from(record: IssueStatusRecord) =
      IssueStatusResponse(
        id = record.apiId.value,
        code = record.code,
        name = record.name,
        statusGroup = record.statusGroup.dbValue,
        rank = record.rank,
        color = record.color,
        isTerminal = record.isTerminal,
      )
  }
}

data class PropertyDefinitionResponse(
  val id: String,
  val code: String,
  val name: String,
  val description: String?,
  val dataType: String,
  val isArray: Boolean,
  val validationSchema: JsonObject,
  val searchConfig: JsonObject,
) {
  companion object {
    fun from(record: PropertyDefinitionRecord) =
      PropertyDefinitionResponse(
        id = record.apiId.value,
        code = record.code,
        name = record.name,
        description = record.description,
        dataType = record.dataType.dbValue,
        isArray = record.isArray,
        validationSchema = record.validationSchema,
        searchConfig = record.searchConfig,
      )
  }
}

data class IssueTypeResponse(
  val id: String,
  val scope: String,
  val code: String,
  val name: String,
  val description: String?,
  val icon: String?,
  val color: String?,
  val rank: Int,
) {
  companion object {
    fun from(record: IssueTypeRecord) =
      IssueTypeResponse(
        id = record.apiId.value,
        scope = record.scope.dbValue,
        code = record.code,
        name = record.name,
        description = record.description,
        icon = record.icon,
        color = record.color,
        rank = record.rank,
      )
  }
}

data class WorkflowResponse(
  val id: String,
  val code: String,
  val name: String,
  val description: String?,
  val version: Int,
  val publishedAt: String?,
) {
  companion object {
    fun from(record: WorkflowRecord) =
      WorkflowResponse(
        id = record.apiId.value,
        code = record.code,
        name = record.name,
        description = record.description,
        version = record.version,
        publishedAt = record.publishedAt?.toString(),
      )
  }
}

data class WorkflowTransitionResponse(
  val id: String,
  val name: String,
  val fromStatusId: String?,
  val toStatusId: String?,
  val rank: Int,
  val permissionCondition: JsonObject,
  val preconditionAst: JsonObject,
  val fields: JsonObject,
) {
  companion object {
    fun from(record: WorkflowTransitionRecord) =
      WorkflowTransitionResponse(
        id = record.apiId.value,
        name = record.name,
        fromStatusId = record.fromStatusApiId?.value,
        toStatusId = record.toStatusApiId?.value,
        rank = record.rank,
        permissionCondition = record.permissionCondition,
        preconditionAst = record.preconditionAst,
        fields = record.fields,
      )
  }
}

data class IssueTypeConfigResponse(
  val id: String,
  val scope: String,
  val issueTypeId: String,
  val workflowId: String,
  val version: Int,
  val nameOverride: String?,
  val iconOverride: String?,
  val colorOverride: String?,
  val rank: Int,
  val createFields: JsonObject,
  val statuses: List<IssueTypeConfigStatusResponse>,
  val properties: List<IssueTypeConfigPropertyResponse>,
) {
  companion object {
    fun from(details: IssueTypeConfigDetails) =
      IssueTypeConfigResponse(
        id = details.config.apiId.value,
        scope = details.config.scope.dbValue,
        issueTypeId = details.config.issueTypeApiId.value,
        workflowId = details.config.workflowApiId.value,
        version = details.config.version,
        nameOverride = details.config.nameOverride,
        iconOverride = details.config.iconOverride,
        colorOverride = details.config.colorOverride,
        rank = details.config.rank,
        createFields = details.config.createFields,
        statuses = details.statuses.map(IssueTypeConfigStatusResponse::from),
        properties = details.properties.map(IssueTypeConfigPropertyResponse::from),
      )
  }
}

data class IssueTypeConfigStatusResponse(
  val statusId: String,
  val code: String,
  val name: String,
  val statusGroup: String,
  val isInitial: Boolean,
  val isTerminal: Boolean,
  val rank: Int,
) {
  companion object {
    fun from(record: IssueTypeConfigStatusRecord) =
      IssueTypeConfigStatusResponse(
        statusId = record.statusApiId.value,
        code = record.code,
        name = record.name,
        statusGroup = record.statusGroup.dbValue,
        isInitial = record.isInitial,
        isTerminal = record.isTerminal,
        rank = record.rank,
      )
  }
}

data class IssueTypeConfigPropertyResponse(
  val propertyId: String,
  val code: String,
  val name: String,
  val dataType: String,
  val validationOverride: JsonObject,
  val rank: Int,
  val displayConfig: JsonObject,
) {
  companion object {
    fun from(record: IssueTypeConfigPropertyRecord) =
      IssueTypeConfigPropertyResponse(
        propertyId = record.propertyApiId.value,
        code = record.code,
        name = record.name,
        dataType = record.dataType.dbValue,
        validationOverride = record.validationOverride,
        rank = record.rank,
        displayConfig = record.displayConfig,
      )
  }
}

data class EffectiveIssueTypeConfigResponse(
  val resolvedFrom: String,
  val config: IssueTypeConfigResponse,
) {
  companion object {
    fun from(record: EffectiveIssueTypeConfig) =
      EffectiveIssueTypeConfigResponse(
        resolvedFrom = record.resolvedFrom.dbValue,
        config = IssueTypeConfigResponse.from(record.config),
      )
  }
}

private fun JsonNode?.toJsonElement(objectMapper: ObjectMapper = ObjectMapper()): JsonElement =
  this?.let { Json.parseToJsonElement(objectMapper.writeValueAsString(it)) }
    ?: Json.parseToJsonElement("{}")

private fun JsonNode?.toJsonObject(objectMapper: ObjectMapper = ObjectMapper()): JsonObject =
  toJsonElement(objectMapper).jsonObject
