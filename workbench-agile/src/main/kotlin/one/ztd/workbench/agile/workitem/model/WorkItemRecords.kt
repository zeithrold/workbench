package one.ztd.workbench.agile.workitem.model

import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import one.ztd.workbench.agile.workitem.query.WorkItemGroupKey
import one.ztd.workbench.agile.workitem.query.WorkItemGroupLabel
import one.ztd.workbench.agile.workitem.richtext.RichTextDocument
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.kernel.common.pagination.WorkItemSearchCursor
import one.ztd.workbench.kernel.common.pagination.WorkItemSearchGroupCursor

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
  val issueTypeId: UUID = UUID(0L, 0L),
  val issueTypeApiId: PublicId,
  val issueTypeConfigApiId: PublicId,
  val key: String,
  val title: String,
  val description: RichTextDocument?,
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
  val description: RichTextDocument?,
  val descriptionPlainText: String? = null,
  val reporterId: UUID,
  val actorUserId: UUID,
  val assigneeApiId: String? = null,
  val priorityApiId: String? = null,
  val sprintApiId: String? = null,
  val parentWorkItemApiId: String? = null,
  val properties: Map<String, JsonElement> = emptyMap(),
)

data class UpdateWorkItemCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemApiId: String,
  val title: String? = null,
  val description: RichTextDocument? = null,
  val descriptionPlainText: String? = null,
  val assigneeApiId: String? = null,
  val priorityApiId: String? = null,
  val sprintApiId: String? = null,
  val clearSprint: Boolean = false,
  val properties: Map<String, JsonElement> = emptyMap(),
  val actorUserId: UUID,
)

data class DeleteWorkItemCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemApiId: String,
  val actorUserId: UUID,
  val deleteReason: String? = null,
)

/** API / service boundary for executing a workflow transition. */
data class TransitionRequest(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemApiId: String,
  val transitionApiId: String,
  val actorUserId: UUID,
  val actorUserApiId: String,
  val properties: Map<String, JsonElement> = emptyMap(),
  val title: String? = null,
  val description: RichTextDocument? = null,
  val comment: RichTextDocument? = null,
)

/** Persistence payload after field reconciliation. */
data class TransitionPersistenceCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemApiId: String,
  val actorUserId: UUID,
  val title: String? = null,
  val description: RichTextDocument? = null,
  val descriptionPlainText: String? = null,
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
  val streamEventId: UUID? = null,
  val streamEventApiId: PublicId? = null,
)

data class WorkItemSearchResult(
  val hits: List<WorkItemSearchHit>,
  val nextCursor: WorkItemSearchCursor?,
)

data class WorkItemIssueTypeSummary(
  val id: String,
  val code: String,
  val name: String,
  val icon: String?,
  val color: String?,
)

data class WorkItemStatusSummary(
  val id: String,
  val code: String,
  val name: String,
  val group: String,
  val color: String?,
  val terminal: Boolean,
)

data class WorkItemPrioritySummary(
  val id: String,
  val code: String,
  val name: String,
  val icon: String?,
  val color: String?,
)

data class WorkItemUserSummary(val id: String, val displayName: String)

data class WorkItemSprintSummary(
  val id: String,
  val name: String,
  val status: String,
  val startAt: OffsetDateTime?,
  val endAt: OffsetDateTime?,
)

data class WorkItemPropertySummary(
  val id: String,
  val code: String,
  val name: String,
  val dataType: String,
  val array: Boolean,
)

data class WorkItemPropertyPresentation(
  val property: WorkItemPropertySummary,
  val value: JsonElement,
  val displayValue: JsonElement,
)

data class WorkItemSearchHit(
  val databaseId: UUID,
  val apiId: String,
  val key: String,
  val title: String,
  val description: RichTextDocument?,
  val projectApiId: String,
  val issueType: WorkItemIssueTypeSummary,
  val issueTypeConfigApiId: String,
  val status: WorkItemStatusSummary,
  val priority: WorkItemPrioritySummary?,
  val reporter: WorkItemUserSummary,
  val assignee: WorkItemUserSummary?,
  val sprint: WorkItemSprintSummary?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
  val properties: Map<String, WorkItemPropertyPresentation>,
  val groupKey: WorkItemGroupKey? = null,
  val groupLabel: WorkItemGroupLabel? = null,
)

data class WorkItemSearchGroupBucket(
  val key: WorkItemGroupKey,
  val label: WorkItemGroupLabel,
  val count: Long,
)

data class WorkItemSearchGroupsPage(
  val groups: List<WorkItemSearchGroupBucket>,
  val nextGroupCursor: WorkItemSearchGroupCursor?,
  val truncated: Boolean,
)
