package ink.doa.workbench.core.workitem.model

import ink.doa.workbench.core.common.ids.PublicId
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

enum class IssueStatusGroup {
  TODO,
  IN_PROGRESS,
  DONE,
}

data class WorkItemRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val projectId: UUID,
  val issueTypeApiId: PublicId,
  val issueTypeConfigApiId: PublicId,
  val key: String,
  val title: String,
  val description: String?,
  val statusId: UUID,
  val statusApiId: PublicId,
  val statusGroup: WorkItemStatusGroup,
  val reporterId: UUID,
  val assigneeId: UUID?,
  val priorityApiId: PublicId?,
  val reporterApiId: PublicId,
  val assigneeApiId: PublicId?,
  val sprintApiId: PublicId?,
  val properties: JsonObject,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class CreateWorkItemCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val issueTypeApiId: String,
  val title: String,
  val description: String?,
  val descriptionPlainText: String? = null,
  val reporterId: UUID,
  val actorUserId: UUID,
  val assigneeApiId: String? = null,
  val priorityApiId: String? = null,
  val sprintApiId: String? = null,
  val properties: Map<String, JsonElement> = emptyMap(),
)

data class UpdateWorkItemCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemApiId: String,
  val title: String? = null,
  val description: String? = null,
  val descriptionPlainText: String? = null,
  val assigneeApiId: String? = null,
  val priorityApiId: String? = null,
  val sprintApiId: String? = null,
  val properties: Map<String, JsonElement> = emptyMap(),
  val actorUserId: UUID,
)

data class TransitionWorkItemCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemApiId: String,
  val transitionApiId: String,
  val actorUserId: UUID,
  val properties: Map<String, JsonElement> = emptyMap(),
  val title: String? = null,
  val description: String? = null,
  val descriptionPlainText: String? = null,
  val comment: String? = null,
  val assigneeApiId: String? = null,
  val priorityApiId: String? = null,
  val sprintApiId: String? = null,
)

data class WorkItemPropertyValue(
  val propertyId: UUID,
  val propertyApiId: PublicId,
  val code: String,
  val dataType: WorkItemPropertyDataType,
  val value: JsonElement,
)

data class WorkItemFormFieldMeta(
  val path: String,
  val editable: Boolean,
  val participation: String,
  val defaultValue: JsonElement? = null,
)

data class WorkItemCommentFormMeta(
  val participation: String,
  val editable: Boolean,
  val defaultTemplate: String? = null,
)

data class WorkItemTransitionOption(
  val id: PublicId,
  val name: String,
  val fromStatusId: PublicId?,
  val toStatusId: PublicId,
  val enabled: Boolean,
  val reason: String? = null,
  val fields: JsonObject,
  val editableFields: List<String> = emptyList(),
  val fieldMeta: List<WorkItemFormFieldMeta> = emptyList(),
  val commentMeta: WorkItemCommentFormMeta? = null,
)

data class WorkItemCreateFormOption(
  val issueTypeId: PublicId,
  val initialStatusId: PublicId,
  val fields: JsonObject,
  val editableFields: List<String> = emptyList(),
  val fieldMeta: List<WorkItemFormFieldMeta> = emptyList(),
)

data class WorkItemMutationResult(
  val workItem: WorkItemRecord,
  val eventType: String,
  val statusHistoryId: UUID? = null,
)

data class WorkItemResponse(
  val apiId: String,
  val key: String,
  val title: String,
  val description: String?,
  val statusGroup: String,
) {
  companion object {
    fun from(record: WorkItemRecord): WorkItemResponse =
      WorkItemResponse(
        apiId = record.apiId.value,
        key = record.key,
        title = record.title,
        description = record.description,
        statusGroup = record.statusGroup.dbValue,
      )
  }
}

data class WorkItemSearchPage(
  val result: WorkItemSearchResult,
  val page: WorkItemSearchPageInfo,
)

data class WorkItemSearchResult(
  val hits: List<WorkItemSearchHit>,
  val total: Long?,
)

data class WorkItemSearchPageInfo(
  val limit: Int,
  val offset: Long,
  val nextOffset: Long?,
)

data class WorkItemSearchHit(
  val apiId: String,
  val key: String,
  val title: String,
  val description: String?,
  val projectApiId: String,
  val issueTypeApiId: String,
  val issueTypeConfigApiId: String,
  val statusApiId: String,
  val statusGroup: String,
  val priorityApiId: String?,
  val reporterApiId: String,
  val assigneeApiId: String?,
  val sprintApiId: String?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
  val properties: JsonObject,
  val sortCursor: WorkItemSearchSortCursor? = null,
)

data class WorkItemSearchSortCursor(val values: JsonObject)
