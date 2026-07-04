package ink.doa.workbench.core.workitem.model

import ink.doa.workbench.core.common.ids.PublicId
import java.time.OffsetDateTime
import java.util.UUID

data class WorkItemCommentRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val issueId: UUID,
  val authorId: UUID,
  val authorApiId: PublicId,
  val body: String,
  val bodyPlainText: String?,
  val bodyFormat: String,
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
  val body: String,
  val bodyPlainText: String? = null,
  val bodyFormat: String = HTML_FORMAT,
  val transitionId: UUID? = null,
  val statusHistoryId: UUID? = null,
) {
  companion object {
    const val HTML_FORMAT = "html"
  }
}

data class UpdateWorkItemCommentCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemApiId: String,
  val commentApiId: String,
  val actorUserId: UUID,
  val body: String,
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
