package one.ztd.workbench.agile.workitem

import java.util.UUID
import one.ztd.workbench.agile.workitem.model.CompletePendingAttachmentCommand
import one.ztd.workbench.agile.workitem.model.CreatePendingAttachmentCommand
import one.ztd.workbench.agile.workitem.model.DeleteWorkItemAttachmentCommand
import one.ztd.workbench.agile.workitem.model.ListWorkItemAttachmentsQuery
import one.ztd.workbench.agile.workitem.model.WorkItemAttachmentRecord

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
