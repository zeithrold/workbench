package one.ztd.workbench.agile.workitem.model

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.agile.workitem.richtext.RichTextDocument
import one.ztd.workbench.kernel.common.ids.PublicId

data class WorkItemCommentCreateResult(
  val record: WorkItemCommentRecord,
  val eventId: UUID? = null,
  val eventApiId: PublicId? = null,
)

data class WorkItemCommentRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val issueId: UUID,
  val authorId: UUID,
  val authorApiId: PublicId,
  val body: RichTextDocument,
  val bodyPlainText: String?,
  val transitionId: UUID?,
  val statusHistoryId: UUID?,
  val editedAt: OffsetDateTime?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class CreateWorkItemCommentCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemApiId: String,
  val authorId: UUID,
  val body: RichTextDocument,
  val bodyPlainText: String? = null,
  val transitionId: UUID? = null,
  val statusHistoryId: UUID? = null,
)

data class UpdateWorkItemCommentCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemApiId: String,
  val commentApiId: String,
  val actorUserId: UUID,
  val body: RichTextDocument,
  val bodyPlainText: String? = null,
)

data class DeleteWorkItemCommentCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemApiId: String,
  val commentApiId: String,
  val actorUserId: UUID,
  val deleteReason: String? = null,
)
