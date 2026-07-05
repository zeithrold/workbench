package ink.doa.workbench.web.workitem

import ink.doa.workbench.core.workitem.model.WorkItemCommentFormMeta
import ink.doa.workbench.core.workitem.model.WorkItemCreateFormOption
import ink.doa.workbench.core.workitem.model.WorkItemFormFieldMeta
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemTransitionOption
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
