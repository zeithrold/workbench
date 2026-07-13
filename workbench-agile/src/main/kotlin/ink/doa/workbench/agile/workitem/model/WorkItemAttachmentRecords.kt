package ink.doa.workbench.agile.workitem.model

import ink.doa.workbench.kernel.common.ids.PublicId
import java.time.OffsetDateTime
import java.util.UUID

enum class AttachmentPurpose(val wireValue: String) {
  STANDALONE("standalone"),
  DESCRIPTION("description"),
  COMMENT("comment");

  companion object {
    fun fromWire(value: String): AttachmentPurpose? = entries.find { it.wireValue == value }
  }
}

enum class AttachmentUploadStatus(val wireValue: String) {
  PENDING("pending"),
  COMPLETED("completed");

  companion object {
    fun fromWire(value: String): AttachmentUploadStatus? = entries.find { it.wireValue == value }
  }
}

data class WorkItemAttachmentRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val issueId: UUID,
  val commentId: UUID?,
  val commentApiId: PublicId?,
  val uploadedBy: UUID,
  val uploadedByApiId: PublicId,
  val filename: String,
  val contentType: String?,
  val byteSize: Long,
  val checksum: String?,
  val storageKey: String,
  val purpose: AttachmentPurpose,
  val uploadStatus: AttachmentUploadStatus,
  val createdAt: OffsetDateTime,
)

data class InitiateWorkItemAttachmentUploadCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val projectApiId: String,
  val workItemApiId: String,
  val uploadedBy: UUID,
  val filename: String,
  val contentType: String?,
  val declaredByteSize: Long,
  val purpose: AttachmentPurpose,
  val commentApiId: String? = null,
)

data class CompleteWorkItemAttachmentUploadCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemApiId: String,
  val attachmentApiId: String,
  val uploadedBy: UUID,
)

data class DeleteWorkItemAttachmentCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemApiId: String,
  val attachmentApiId: String,
  val actorUserId: UUID,
  val deleteReason: String? = null,
)

data class ListWorkItemAttachmentsQuery(
  val tenantId: UUID,
  val issueId: UUID,
  val purpose: AttachmentPurpose? = null,
  val commentApiId: String? = null,
  val limit: Int,
  val offset: Long,
)

data class CreatePendingAttachmentCommand(
  val upload: InitiateWorkItemAttachmentUploadCommand,
  val issueId: UUID,
  val commentId: UUID?,
  val attachmentId: UUID,
  val apiId: PublicId,
  val storageKey: String,
)

data class CompletePendingAttachmentCommand(
  val tenantId: UUID,
  val issueId: UUID,
  val attachmentApiId: String,
  val uploadedBy: UUID,
  val byteSize: Long,
  val checksum: String,
)
