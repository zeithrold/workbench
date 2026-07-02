package doa.ink.workbench.core.workitem.model

import doa.ink.workbench.core.common.ids.PublicId
import java.time.OffsetDateTime
import java.util.UUID

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
