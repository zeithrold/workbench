package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.agile.workitem.model.UpdateWorkItemCommand
import ink.doa.workbench.agile.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.agile.workitem.model.WorkItemRecord
import java.time.OffsetDateTime
import java.util.UUID

data class WorkItemActivityContext(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemId: UUID,
  val actorUserId: UUID,
  val occurredAt: OffsetDateTime,
)

data class WorkItemUpdateActivityInput(
  val context: WorkItemActivityContext,
  val before: WorkItemRecord,
  val after: WorkItemRecord,
  val command: UpdateWorkItemCommand,
  val propertyValues: List<WorkItemPropertyValue>,
)

data class WorkItemStatusChangedInput(
  val context: WorkItemActivityContext,
  val fromStatusId: UUID,
  val toStatusId: UUID,
  val transitionId: UUID,
)

data class WorkItemCommentCreatedInput(
  val context: WorkItemActivityContext,
  val commentApiId: String,
  val plainTextPreview: String?,
)

data class WorkItemCommentDeletedInput(
  val context: WorkItemActivityContext,
  val commentApiId: String,
  val plainTextPreview: String?,
  val deleteReason: String? = null,
)
