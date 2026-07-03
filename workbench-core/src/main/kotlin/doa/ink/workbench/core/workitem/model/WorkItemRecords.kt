package doa.ink.workbench.core.workitem.model

import doa.ink.workbench.core.common.ids.PublicId
import java.time.OffsetDateTime
import java.util.UUID
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
  val statusGroup: IssueStatusGroup,
  val updatedAt: OffsetDateTime,
)

data class CreateWorkItemCommand(
  val tenantId: UUID,
  val projectApiId: String,
  val issueTypeApiId: String,
  val title: String,
  val description: String?,
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
        statusGroup = record.statusGroup.name.lowercase(),
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

data class WorkItemSearchSortCursor(
  val values: JsonObject,
)
