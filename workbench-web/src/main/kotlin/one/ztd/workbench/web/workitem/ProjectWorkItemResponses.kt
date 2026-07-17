package one.ztd.workbench.web.workitem

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import one.ztd.workbench.agile.workitem.model.WorkItemCommentFormMeta
import one.ztd.workbench.agile.workitem.model.WorkItemCreateFormOption
import one.ztd.workbench.agile.workitem.model.WorkItemFormFieldMeta
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyPresentation
import one.ztd.workbench.agile.workitem.model.WorkItemSearchHit
import one.ztd.workbench.agile.workitem.model.WorkItemTransitionOption

data class WorkItemResponse(
  val id: String,
  val key: String,
  val title: String,
  val description: RichTextDocumentPayload?,
  val projectId: String,
  val issueType: WorkItemIssueTypeSummaryResponse,
  val issueTypeConfigId: String,
  val status: WorkItemStatusSummaryResponse,
  val priority: WorkItemPrioritySummaryResponse?,
  val reporter: WorkItemUserSummaryResponse,
  val assignee: WorkItemUserSummaryResponse?,
  val sprint: WorkItemSprintSummaryResponse?,
  val properties: Map<String, WorkItemPropertyPresentationResponse>,
  val createdAt: java.time.OffsetDateTime,
  val updatedAt: java.time.OffsetDateTime,
  val groupKey: kotlinx.serialization.json.JsonObject? = null,
  val groupLabel: WorkItemGroupLabelResponse? = null,
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
        createdAt = hit.createdAt,
        updatedAt = hit.updatedAt,
        groupKey = hit.groupKey?.toJsonObject(),
        groupLabel = hit.groupLabel?.let(WorkItemGroupLabelResponse::from),
      )
  }
}

data class WorkItemIssueTypeSummaryResponse(
  val id: String,
  val code: String,
  val name: String,
  val icon: String?,
  val color: String?,
) {
  companion object {
    fun from(value: one.ztd.workbench.agile.workitem.model.WorkItemIssueTypeSummary) =
      WorkItemIssueTypeSummaryResponse(value.id, value.code, value.name, value.icon, value.color)
  }
}

data class WorkItemStatusSummaryResponse(
  val id: String,
  val code: String,
  val name: String,
  val group: String,
  val color: String?,
  val terminal: Boolean,
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
  val id: String,
  val code: String,
  val name: String,
  val icon: String?,
  val color: String?,
) {
  companion object {
    fun from(value: one.ztd.workbench.agile.workitem.model.WorkItemPrioritySummary) =
      WorkItemPrioritySummaryResponse(value.id, value.code, value.name, value.icon, value.color)
  }
}

data class WorkItemUserSummaryResponse(val id: String, val displayName: String) {
  companion object {
    fun from(value: one.ztd.workbench.agile.workitem.model.WorkItemUserSummary) =
      WorkItemUserSummaryResponse(value.id, value.displayName)
  }
}

data class WorkItemSprintSummaryResponse(
  val id: String,
  val name: String,
  val status: String,
  val startAt: java.time.OffsetDateTime?,
  val endAt: java.time.OffsetDateTime?,
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
