package ink.doa.workbench.web.workitem

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import ink.doa.workbench.agile.workitem.WorkItemQueryService
import ink.doa.workbench.agile.workitem.WorkItemService
import ink.doa.workbench.agile.workitem.WorkItemTransitionService
import ink.doa.workbench.core.common.context.ProjectRequestContext
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.WorkItemSearchPageRequest
import ink.doa.workbench.core.workitem.WorkItemSearchScope
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.DeleteWorkItemCommand
import ink.doa.workbench.core.workitem.model.TransitionWorkItemCommand
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemCommentFormMeta
import ink.doa.workbench.core.workitem.model.WorkItemCreateFormOption
import ink.doa.workbench.core.workitem.model.WorkItemFormFieldMeta
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemSearchPage
import ink.doa.workbench.core.workitem.model.WorkItemTransitionOption
import ink.doa.workbench.core.workitem.query.WorkItemQueryParser
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.ProjectScoped
import ink.doa.workbench.web.api.ResourceId
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.net.URI
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
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
@RequestMapping("/api/projects/{id}/work-items")
@Tag(name = "Project Work Items", description = "Project-scoped work item runtime APIs.")
@SessionSecured
@StandardErrorResponses
class ProjectWorkItemController(
  private val service: WorkItemService,
  private val transitionService: WorkItemTransitionService,
  private val queryService: WorkItemQueryService,
) {
  private val objectMapper = ObjectMapper()
  private val queryParser = WorkItemQueryParser()

  @GetMapping
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.view", resource = "issue")
  @Operation(summary = "List project work items")
  suspend fun list(
    @RequestParam(defaultValue = "50") limit: Int,
    @RequestParam(defaultValue = "0") offset: Long,
    projectContext: ProjectRequestContext,
  ): List<WorkItemResponse> =
    service
      .list(projectContext.tenant.id, projectContext.project.id, limit, offset)
      .map(WorkItemResponse::from)

  @PostMapping
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.create", resource = "issue")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a work item")
  suspend fun create(
    @Valid @RequestBody request: CreateWorkItemRequest,
    projectContext: ProjectRequestContext,
  ): ResponseEntity<WorkItemResponse> {
    val result = service.create(request.toCommand(projectContext, actorUserId(projectContext)))
    val response = WorkItemResponse.from(result.workItem)
    return ResponseEntity.created(location(projectContext.project.publicId.value, response.id))
      .body(response)
  }

  @PostMapping("/search")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.view", resource = "issue")
  @Operation(summary = "Search project work items")
  suspend fun search(
    @Valid @RequestBody request: WorkItemSearchRequest,
    projectContext: ProjectRequestContext,
  ): WorkItemSearchPage =
    queryService.search(
      scope = WorkItemSearchScope(projectContext.tenant.id, projectContext.project.id),
      query = queryParser.parse(request.query.toJsonElement(objectMapper)),
      page = WorkItemSearchPageRequest(request.limit ?: 50, request.offset ?: 0),
    )

  @GetMapping("/create-form")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.create", resource = "issue")
  @Operation(summary = "Get work item create form")
  suspend fun createForm(
    @RequestParam issueTypeId: String,
    projectContext: ProjectRequestContext,
  ): WorkItemCreateFormResponse =
    WorkItemCreateFormResponse.from(
      service.availableCreateForm(
        projectContext.tenant.id,
        projectContext.project.id,
        issueTypeId,
        actorUserId(projectContext),
      )
    )

  @GetMapping("/{workItemId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.view", resource = "issue")
  @Operation(summary = "Get a work item")
  suspend fun get(
    @PathVariable @ResourceId workItemId: String,
    projectContext: ProjectRequestContext,
  ): WorkItemResponse =
    WorkItemResponse.from(
      service.get(projectContext.tenant.id, projectContext.project.id, workItemId)
    )

  @PatchMapping("/{workItemId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.update", resource = "issue")
  @Operation(summary = "Update a work item")
  suspend fun update(
    @PathVariable @ResourceId workItemId: String,
    @Valid @RequestBody request: UpdateWorkItemRequest,
    projectContext: ProjectRequestContext,
  ): WorkItemResponse =
    WorkItemResponse.from(
      service
        .update(request.toCommand(projectContext, workItemId, actorUserId(projectContext)))
        .workItem
    )

  @DeleteMapping("/{workItemId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.delete", resource = "issue")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a work item")
  suspend fun delete(
    @PathVariable @ResourceId workItemId: String,
    @RequestBody(required = false) request: DeleteWorkItemRequest?,
    projectContext: ProjectRequestContext,
  ) {
    service.delete(
      DeleteWorkItemCommand(
        tenantId = projectContext.tenant.id,
        projectId = projectContext.project.id,
        workItemApiId = workItemId,
        actorUserId = actorUserId(projectContext),
        deleteReason = request?.deleteReason,
      )
    )
  }

  @GetMapping("/{workItemId}/transitions")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.transition", resource = "issue")
  @Operation(summary = "List available work item transitions")
  suspend fun transitions(
    @PathVariable @ResourceId workItemId: String,
    projectContext: ProjectRequestContext,
  ): List<WorkItemTransitionResponse> =
    transitionService
      .availableTransitions(
        projectContext.tenant.id,
        projectContext.project.id,
        workItemId,
        actorUserId(projectContext),
      )
      .map(WorkItemTransitionResponse::from)

  @PostMapping("/{workItemId}/transition")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.transition", resource = "issue")
  @Operation(summary = "Transition a work item")
  suspend fun transition(
    @PathVariable @ResourceId workItemId: String,
    @Valid @RequestBody request: TransitionWorkItemRequest,
    projectContext: ProjectRequestContext,
  ): WorkItemResponse =
    WorkItemResponse.from(
      transitionService
        .transition(request.toCommand(projectContext, workItemId, actorUserId(projectContext)))
        .workItem
    )

  private fun actorUserId(projectContext: ProjectRequestContext) =
    projectContext.actor?.id
      ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)

  private fun location(projectId: String, workItemId: String): URI =
    URI.create("/api/projects/$projectId/work-items/$workItemId")
}

data class CreateWorkItemRequest(
  @field:NotBlank val issueTypeId: String,
  @field:NotBlank @field:Size(max = 500) val title: String,
  val description: String? = null,
  val assigneeId: String? = null,
  val priorityId: String? = null,
  val sprintId: String? = null,
  val properties: JsonNode? = null,
) {
  fun toCommand(
    context: ProjectRequestContext,
    actorUserId: java.util.UUID,
  ): CreateWorkItemCommand =
    CreateWorkItemCommand(
      tenantId = context.tenant.id,
      projectId = context.project.id,
      issueTypeApiId = issueTypeId,
      title = title,
      description = description,
      reporterId = actorUserId,
      actorUserId = actorUserId,
      assigneeApiId = assigneeId,
      priorityApiId = priorityId,
      sprintApiId = sprintId,
      properties = properties.toJsonObject().toMap(),
    )
}

data class UpdateWorkItemRequest(
  @field:Size(max = 500) val title: String? = null,
  val description: String? = null,
  val assigneeId: String? = null,
  val priorityId: String? = null,
  val sprintId: String? = null,
  val properties: JsonNode? = null,
) {
  fun toCommand(
    context: ProjectRequestContext,
    workItemId: String,
    actorUserId: java.util.UUID,
  ): UpdateWorkItemCommand =
    UpdateWorkItemCommand(
      tenantId = context.tenant.id,
      projectId = context.project.id,
      workItemApiId = workItemId,
      title = title,
      description = description,
      assigneeApiId = assigneeId,
      priorityApiId = priorityId,
      sprintApiId = sprintId,
      properties = properties.toJsonObject().toMap(),
      actorUserId = actorUserId,
    )
}

data class TransitionWorkItemRequest(
  @field:NotBlank val transitionId: String,
  val title: String? = null,
  val description: String? = null,
  val comment: String? = null,
  val properties: JsonNode? = null,
) {
  fun toCommand(
    context: ProjectRequestContext,
    workItemId: String,
    actorUserId: java.util.UUID,
  ): TransitionWorkItemCommand =
    TransitionWorkItemCommand(
      tenantId = context.tenant.id,
      projectId = context.project.id,
      workItemApiId = workItemId,
      transitionApiId = transitionId,
      actorUserId = actorUserId,
      title = title,
      description = description,
      comment = comment,
      properties = properties.toJsonObject().toMap(),
    )
}

data class DeleteWorkItemRequest(val deleteReason: String? = null)

data class WorkItemSearchRequest(
  val query: JsonNode,
  val limit: Int? = null,
  val offset: Long? = null,
)

data class WorkItemResponse(
  val id: String,
  val key: String,
  val title: String,
  val description: String?,
  val issueTypeId: String,
  val issueTypeConfigId: String,
  val statusId: String,
  val statusGroup: String,
  val priorityId: String?,
  val reporterId: String,
  val assigneeId: String?,
  val sprintId: String?,
  val properties: JsonObject,
  val createdAt: String,
  val updatedAt: String,
) {
  companion object {
    fun from(record: WorkItemRecord): WorkItemResponse =
      WorkItemResponse(
        id = record.apiId.value,
        key = record.key,
        title = record.title,
        description = record.description,
        issueTypeId = record.issueTypeApiId.value,
        issueTypeConfigId = record.issueTypeConfigApiId.value,
        statusId = record.statusApiId.value,
        statusGroup = record.statusGroup.dbValue,
        priorityId = record.priorityApiId?.value,
        reporterId = record.reporterApiId.value,
        assigneeId = record.assigneeApiId?.value,
        sprintId = record.sprintApiId?.value,
        properties = record.properties,
        createdAt = record.createdAt.toString(),
        updatedAt = record.updatedAt.toString(),
      )
  }
}

data class WorkItemTransitionResponse(
  val id: String,
  val name: String,
  val fromStatusId: String?,
  val toStatusId: String,
  val enabled: Boolean,
  val reason: String?,
  val fields: JsonObject,
  val editableFields: List<String>,
  val fieldMeta: List<WorkItemFormFieldMetaResponse>,
  val commentMeta: WorkItemCommentFormMetaResponse?,
) {
  companion object {
    fun from(option: WorkItemTransitionOption): WorkItemTransitionResponse =
      WorkItemTransitionResponse(
        id = option.id.value,
        name = option.name,
        fromStatusId = option.fromStatusId?.value,
        toStatusId = option.toStatusId.value,
        enabled = option.enabled,
        reason = option.reason,
        fields = option.fields,
        editableFields = option.editableFields,
        fieldMeta = option.fieldMeta.map(WorkItemFormFieldMetaResponse::from),
        commentMeta = option.commentMeta?.let(WorkItemCommentFormMetaResponse::from),
      )
  }
}

data class WorkItemCreateFormResponse(
  val issueTypeId: String,
  val initialStatusId: String,
  val fields: JsonObject,
  val editableFields: List<String>,
  val fieldMeta: List<WorkItemFormFieldMetaResponse>,
) {
  companion object {
    fun from(option: WorkItemCreateFormOption): WorkItemCreateFormResponse =
      WorkItemCreateFormResponse(
        issueTypeId = option.issueTypeId.value,
        initialStatusId = option.initialStatusId.value,
        fields = option.fields,
        editableFields = option.editableFields,
        fieldMeta = option.fieldMeta.map(WorkItemFormFieldMetaResponse::from),
      )
  }
}

data class WorkItemFormFieldMetaResponse(
  val path: String,
  val editable: Boolean,
  val participation: String,
  val defaultValue: JsonElement?,
) {
  companion object {
    fun from(meta: WorkItemFormFieldMeta): WorkItemFormFieldMetaResponse =
      WorkItemFormFieldMetaResponse(
        path = meta.path,
        editable = meta.editable,
        participation = meta.participation,
        defaultValue = meta.defaultValue,
      )
  }
}

data class WorkItemCommentFormMetaResponse(
  val participation: String,
  val editable: Boolean,
  val defaultTemplate: String?,
) {
  companion object {
    fun from(meta: WorkItemCommentFormMeta): WorkItemCommentFormMetaResponse =
      WorkItemCommentFormMetaResponse(
        participation = meta.participation,
        editable = meta.editable,
        defaultTemplate = meta.defaultTemplate,
      )
  }
}
