package one.ztd.workbench.web.workitem

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import one.ztd.workbench.agile.workitem.model.WorkItemCommentFormMeta
import one.ztd.workbench.agile.workitem.model.WorkItemCreateFormOption
import one.ztd.workbench.agile.workitem.model.WorkItemFormFieldMeta
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyPresentation
import one.ztd.workbench.agile.workitem.model.WorkItemSearchHit
import one.ztd.workbench.agile.workitem.model.WorkItemTransitionOption

data class WorkItemResponse(
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val id: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val key: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val title: String,
  @get:Schema(nullable = true) val description: RichTextDocumentPayload?,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val projectId: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  val issueType: WorkItemIssueTypeSummaryResponse,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val issueTypeConfigId: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  val status: WorkItemStatusSummaryResponse,
  @get:Schema(nullable = true) val priority: WorkItemPrioritySummaryResponse?,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  val reporter: WorkItemUserSummaryResponse,
  @get:Schema(nullable = true) val assignee: WorkItemUserSummaryResponse?,
  @get:Schema(nullable = true) val sprint: WorkItemSprintSummaryResponse?,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  val properties: Map<String, WorkItemPropertyPresentationResponse>,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  val fieldCapabilities: Map<String, WorkItemFieldCapabilityResponse>,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  val createdAt: java.time.OffsetDateTime,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  val updatedAt: java.time.OffsetDateTime,
  @get:Schema(nullable = true) val groupKey: kotlinx.serialization.json.JsonObject? = null,
  @get:Schema(nullable = true) val groupLabel: WorkItemGroupLabelResponse? = null,
) {
  companion object {
    fun from(hit: WorkItemSearchHit): WorkItemResponse =
      WorkItemResponse(
        id = hit.apiId,
        key = hit.key,
        title = hit.title,
        description = hit.description?.let(RichTextDocumentPayload::from),
        projectId = hit.projectApiId,
        issueType = WorkItemIssueTypeSummaryResponse.from(hit.issueType),
        issueTypeConfigId = hit.issueTypeConfigApiId,
        status = WorkItemStatusSummaryResponse.from(hit.status),
        priority = hit.priority?.let(WorkItemPrioritySummaryResponse::from),
        reporter = WorkItemUserSummaryResponse.from(hit.reporter),
        assignee = hit.assignee?.let(WorkItemUserSummaryResponse::from),
        sprint = hit.sprint?.let(WorkItemSprintSummaryResponse::from),
        properties =
          hit.properties.mapValues { WorkItemPropertyPresentationResponse.from(it.value) },
        fieldCapabilities =
          hit.fieldCapabilities.mapValues { WorkItemFieldCapabilityResponse.from(it.value) },
        createdAt = hit.createdAt,
        updatedAt = hit.updatedAt,
        groupKey = hit.groupKey?.toJsonObject(),
        groupLabel = hit.groupLabel?.let(WorkItemGroupLabelResponse::from),
      )
  }
}

data class WorkItemFieldCapabilityResponse(
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  val state: one.ztd.workbench.agile.workitem.model.WorkItemFieldCapabilityState,
  @get:Schema(nullable = true) val reason: String?,
) {
  companion object {
    fun from(value: one.ztd.workbench.agile.workitem.model.WorkItemFieldCapability) =
      WorkItemFieldCapabilityResponse(value.state, value.reason)
  }
}

data class WorkItemDisplayFieldDefinitionResponse(
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val key: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val name: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val dataType: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val array: Boolean,
  @get:Schema(nullable = true) val propertyId: String?,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val validation: JsonObject,
) {
  companion object {
    fun from(value: one.ztd.workbench.agile.workitem.WorkItemDisplayFieldDefinition) =
      WorkItemDisplayFieldDefinitionResponse(
        value.key,
        value.name,
        value.dataType,
        value.array,
        value.propertyId,
        value.validation,
      )
  }
}

data class WorkItemFieldOptionResponse(
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val id: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val label: String,
  @get:Schema(nullable = true) val description: String?,
  @get:Schema(nullable = true) val color: String?,
  @get:Schema(nullable = true) val icon: String?,
  @get:Schema(nullable = true) val status: String?,
) {
  companion object {
    fun from(value: one.ztd.workbench.agile.workitem.WorkItemFieldOption) =
      WorkItemFieldOptionResponse(
        value.id,
        value.label,
        value.description,
        value.color,
        value.icon,
        value.status,
      )
  }
}

data class WorkItemIssueTypeSummaryResponse(
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val id: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val code: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val name: String,
  @get:Schema(nullable = true) val icon: String?,
  @get:Schema(nullable = true) val color: String?,
) {
  companion object {
    fun from(value: one.ztd.workbench.agile.workitem.model.WorkItemIssueTypeSummary) =
      WorkItemIssueTypeSummaryResponse(value.id, value.code, value.name, value.icon, value.color)
  }
}

data class WorkItemStatusSummaryResponse(
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val id: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val code: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val name: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val group: String,
  @get:Schema(nullable = true) val color: String?,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val terminal: Boolean,
) {
  companion object {
    fun from(value: one.ztd.workbench.agile.workitem.model.WorkItemStatusSummary) =
      WorkItemStatusSummaryResponse(
        value.id,
        value.code,
        value.name,
        value.group,
        value.color,
        value.terminal,
      )
  }
}

data class WorkItemPrioritySummaryResponse(
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val id: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val code: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val name: String,
  @get:Schema(nullable = true) val icon: String?,
  @get:Schema(nullable = true) val color: String?,
) {
  companion object {
    fun from(value: one.ztd.workbench.agile.workitem.model.WorkItemPrioritySummary) =
      WorkItemPrioritySummaryResponse(value.id, value.code, value.name, value.icon, value.color)
  }
}

data class WorkItemUserSummaryResponse(
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val id: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val displayName: String,
) {
  companion object {
    fun from(value: one.ztd.workbench.agile.workitem.model.WorkItemUserSummary) =
      WorkItemUserSummaryResponse(value.id, value.displayName)
  }
}

data class WorkItemSprintSummaryResponse(
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val id: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val name: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val status: String,
  @get:Schema(nullable = true) val startAt: java.time.OffsetDateTime?,
  @get:Schema(nullable = true) val endAt: java.time.OffsetDateTime?,
) {
  companion object {
    fun from(value: one.ztd.workbench.agile.workitem.model.WorkItemSprintSummary) =
      WorkItemSprintSummaryResponse(value.id, value.name, value.status, value.startAt, value.endAt)
  }
}

data class WorkItemPropertySummaryResponse(
  val id: String,
  val code: String,
  val name: String,
  val dataType: String,
  val array: Boolean,
)

data class WorkItemPropertyPresentationResponse(
  val property: WorkItemPropertySummaryResponse,
  val value: JsonElement,
  val displayValue: JsonElement,
) {
  companion object {
    fun from(value: WorkItemPropertyPresentation) =
      WorkItemPropertyPresentationResponse(
        property =
          WorkItemPropertySummaryResponse(
            id = value.property.id,
            code = value.property.code,
            name = value.property.name,
            dataType = value.property.dataType,
            array = value.property.array,
          ),
        value = value.value,
        displayValue = value.displayValue,
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
  @get:Schema(nullable = true) val targetStatus: WorkItemStatusSummaryResponse?,
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
        targetStatus = option.targetStatus?.let(WorkItemStatusSummaryResponse::from),
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
