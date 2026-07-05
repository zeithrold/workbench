package ink.doa.workbench.core.workitem

import ink.doa.workbench.core.workitem.model.CompletePendingAttachmentCommand
import ink.doa.workbench.core.workitem.model.CreatePendingAttachmentCommand
import ink.doa.workbench.core.workitem.model.DeleteWorkItemAttachmentCommand
import ink.doa.workbench.core.workitem.model.ListWorkItemAttachmentsQuery
import ink.doa.workbench.core.workitem.model.WorkItemAttachmentRecord
import java.util.UUID

interface WorkItemAttachmentRepository {
  suspend fun listByWorkItem(query: ListWorkItemAttachmentsQuery): List<WorkItemAttachmentRecord>

  suspend fun createPending(command: CreatePendingAttachmentCommand): WorkItemAttachmentRecord

  suspend fun completePending(command: CompletePendingAttachmentCommand): WorkItemAttachmentRecord

  suspend fun findByApiId(
    tenantId: UUID,
    issueId: UUID,
    attachmentApiId: String,
  ): WorkItemAttachmentRecord?

  suspend fun findPendingByApiId(
    tenantId: UUID,
    issueId: UUID,
    attachmentApiId: String,
    uploadedBy: UUID,
  ): WorkItemAttachmentRecord?

  suspend fun softDelete(
    command: DeleteWorkItemAttachmentCommand,
    issueId: UUID,
  ): WorkItemAttachmentRecord

  suspend fun resolveIssueId(tenantId: UUID, projectId: UUID, workItemApiId: String): UUID?

  suspend fun resolveCommentId(
    tenantId: UUID,
    issueId: UUID,
    commentApiId: String,
  ): UUID?
}
