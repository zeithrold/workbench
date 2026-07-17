package one.ztd.workbench.data.repository.workitem

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.agile.workitem.model.UpdateWorkItemCommand
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyValue
import one.ztd.workbench.agile.workitem.model.WorkItemRecord

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
