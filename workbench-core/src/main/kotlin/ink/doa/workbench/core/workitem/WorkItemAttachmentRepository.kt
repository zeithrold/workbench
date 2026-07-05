package ink.doa.workbench.core.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.AttachmentPurpose
import ink.doa.workbench.core.workitem.model.DeleteWorkItemAttachmentCommand
import ink.doa.workbench.core.workitem.model.InitiateWorkItemAttachmentUploadCommand
import ink.doa.workbench.core.workitem.model.WorkItemAttachmentRecord
import java.util.UUID

interface WorkItemAttachmentRepository {
  suspend fun listByWorkItem(
    tenantId: UUID,
    issueId: UUID,
    purpose: AttachmentPurpose?,
    commentApiId: String?,
    limit: Int,
    offset: Long,
  ): List<WorkItemAttachmentRecord>

  suspend fun createPending(
    command: InitiateWorkItemAttachmentUploadCommand,
    issueId: UUID,
    commentId: UUID?,
    attachmentId: UUID,
    apiId: PublicId,
    storageKey: String,
  ): WorkItemAttachmentRecord

  suspend fun completePending(
    tenantId: UUID,
    issueId: UUID,
    attachmentApiId: String,
    uploadedBy: UUID,
    byteSize: Long,
    checksum: String,
  ): WorkItemAttachmentRecord

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
