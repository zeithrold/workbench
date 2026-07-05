@file:Suppress("TooManyFunctions")

package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.storage.BlobObjectHead
import ink.doa.workbench.core.storage.BlobStorage
import ink.doa.workbench.core.storage.BlobStorageObjectNotFoundException
import ink.doa.workbench.core.storage.PresignedBlobRequest
import ink.doa.workbench.core.storage.StorageLimits
import ink.doa.workbench.core.workitem.WorkItemAttachmentRepository
import ink.doa.workbench.core.workitem.model.AttachmentPurpose
import ink.doa.workbench.core.workitem.model.CompleteWorkItemAttachmentUploadCommand
import ink.doa.workbench.core.workitem.model.DeleteWorkItemAttachmentCommand
import ink.doa.workbench.core.workitem.model.InitiateWorkItemAttachmentUploadCommand
import ink.doa.workbench.core.workitem.model.WorkItemAttachmentRecord
import ink.doa.workbench.core.workitem.richtext.AttachmentReferenceParser
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class WorkItemAttachmentService(
  private val attachments: WorkItemAttachmentRepository,
  private val blobStorage: BlobStorage,
  private val storageLimits: StorageLimits,
) {
  suspend fun list(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
    purpose: AttachmentPurpose?,
    commentApiId: String?,
    limit: Int = 50,
    offset: Long = 0,
  ): List<WorkItemAttachmentRecord> {
    val issueId = requireIssueId(tenantId, projectId, workItemApiId)
    return attachments.listByWorkItem(
      tenantId = tenantId,
      issueId = issueId,
      purpose = purpose,
      commentApiId = commentApiId,
      limit = limit,
      offset = offset,
    )
  }

  suspend fun get(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
    attachmentApiId: String,
  ): WorkItemAttachmentRecord {
    val issueId = requireIssueId(tenantId, projectId, workItemApiId)
    return attachments.findByApiId(tenantId, issueId, attachmentApiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_ATTACHMENT_NOT_FOUND)
  }

  suspend fun initiateUpload(
    command: InitiateWorkItemAttachmentUploadCommand
  ): WorkItemAttachmentUploadSession {
    uploadValidationError(command)?.let { throw InvalidRequestException(it) }
    val issueId = requireIssueId(command.tenantId, command.projectId, command.workItemApiId)
    val commentId = resolveCommentId(command, issueId)
    val attachmentId = UUID.randomUUID()
    val apiId = PublicId.new("att")
    val storageKey = buildStorageKey(command.tenantId, issueId, attachmentId, command.filename)
    val pending =
      attachments.createPending(
        command = command,
        issueId = issueId,
        commentId = commentId,
        attachmentId = attachmentId,
        apiId = apiId,
        storageKey = storageKey,
      )
    val presigned =
      blobStorage.presignPut(
        key = storageKey,
        contentType = command.contentType,
        contentLength = command.declaredByteSize,
      )
    return WorkItemAttachmentUploadSession(
      attachmentApiId = pending.apiId.value,
      presigned = presigned,
    )
  }

  suspend fun completeUpload(
    command: CompleteWorkItemAttachmentUploadCommand
  ): WorkItemAttachmentRecord {
    val issueId = requireIssueId(command.tenantId, command.projectId, command.workItemApiId)
    val pending =
      attachments.findPendingByApiId(
        tenantId = command.tenantId,
        issueId = issueId,
        attachmentApiId = command.attachmentApiId,
        uploadedBy = command.uploadedBy,
      )
        ?: throw ResourceNotFoundException(
          WorkbenchErrorCode.RESOURCE_WORK_ITEM_ATTACHMENT_NOT_FOUND
        )
    val headResult = resolveUploadHead(pending)
    val validationError = completeUploadValidationError(pending, headResult)
    if (validationError != null) {
      if (validationError == WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_UPLOAD_SIZE_MISMATCH) {
        runCatching { blobStorage.delete(pending.storageKey) }
      }
      throw InvalidRequestException(validationError)
    }
    val head = headResult.getOrThrow()
    return attachments.completePending(
      tenantId = command.tenantId,
      issueId = issueId,
      attachmentApiId = command.attachmentApiId,
      uploadedBy = command.uploadedBy,
      byteSize = head.contentLength,
      checksum = normalizeChecksum(head.etag),
    )
  }

  private suspend fun resolveUploadHead(pending: WorkItemAttachmentRecord): Result<BlobObjectHead> =
    runCatching {
      blobStorage.head(pending.storageKey)
    }

  private fun completeUploadValidationError(
    pending: WorkItemAttachmentRecord,
    headResult: Result<BlobObjectHead>,
  ): WorkbenchErrorCode? =
    when {
      isUploadExpired(pending.createdAt) -> WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_UPLOAD_EXPIRED
      headResult.isFailure -> WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_UPLOAD_INCOMPLETE
      headResult.getOrThrow().contentLength != pending.byteSize ->
        WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_UPLOAD_SIZE_MISMATCH
      else -> null
    }

  suspend fun presignedDownloadUrl(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
    attachmentApiId: String,
  ): PresignedBlobRequest {
    val attachment = get(tenantId, projectId, workItemApiId, attachmentApiId)
    return try {
      blobStorage.presignGet(attachment.storageKey)
    } catch (_: BlobStorageObjectNotFoundException) {
      throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_ATTACHMENT_NOT_FOUND)
    }
  }

  suspend fun contentRedirectUrl(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
    attachmentApiId: String,
  ): String = presignedDownloadUrl(tenantId, projectId, workItemApiId, attachmentApiId).url

  suspend fun delete(command: DeleteWorkItemAttachmentCommand): WorkItemAttachmentRecord {
    val issueId = requireIssueId(command.tenantId, command.projectId, command.workItemApiId)
    val deleted = attachments.softDelete(command, issueId)
    runCatching { blobStorage.delete(deleted.storageKey) }
    return deleted
  }

  fun contentUrl(
    projectApiId: String,
    workItemApiId: String,
    attachmentApiId: String,
  ): String =
    AttachmentReferenceParser.buildContentUrl(projectApiId, workItemApiId, attachmentApiId)

  private suspend fun resolveCommentId(
    command: InitiateWorkItemAttachmentUploadCommand,
    issueId: UUID,
  ): UUID? {
    if (command.purpose != AttachmentPurpose.COMMENT) return null
    return attachments.resolveCommentId(command.tenantId, issueId, command.commentApiId!!)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_COMMENT_NOT_FOUND)
  }

  private suspend fun requireIssueId(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
  ): UUID =
    attachments.resolveIssueId(tenantId, projectId, workItemApiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)

  private fun uploadValidationError(
    command: InitiateWorkItemAttachmentUploadCommand
  ): WorkbenchErrorCode? =
    when {
      command.declaredByteSize <= 0L -> WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_FILE_REQUIRED
      command.declaredByteSize > storageLimits.maxFileSizeBytes ->
        WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_TOO_LARGE
      command.purpose == AttachmentPurpose.COMMENT && command.commentApiId.isNullOrBlank() ->
        WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_COMMENT_REQUIRED
      !isAllowedContentType(command.contentType) ->
        WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_CONTENT_TYPE_UNSUPPORTED
      else -> null
    }

  private fun isUploadExpired(createdAt: OffsetDateTime): Boolean {
    val expiresAt = createdAt.plus(storageLimits.presignUploadTtl)
    return OffsetDateTime.now(ZoneOffset.UTC).isAfter(expiresAt)
  }

  private fun isAllowedContentType(contentType: String?): Boolean {
    if (contentType == null) return true
    val normalized = contentType.substringBefore(';').trim().lowercase()
    return ALLOWED_CONTENT_TYPE_PREFIXES.any { prefix -> normalized.startsWith(prefix) } ||
      normalized in ALLOWED_CONTENT_TYPES
  }

  private fun buildStorageKey(
    tenantId: UUID,
    issueId: UUID,
    attachmentId: UUID,
    filename: String,
  ): String = "${tenantId}/${issueId}/${attachmentId}/${sanitizeFilename(filename)}"

  private fun sanitizeFilename(filename: String): String =
    filename.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(200).ifBlank { "attachment" }

  private fun normalizeChecksum(etag: String?): String = etag?.trim('"')?.lowercase() ?: "unknown"

  private companion object {
    val ALLOWED_CONTENT_TYPE_PREFIXES = listOf("image/", "text/")
    val ALLOWED_CONTENT_TYPES =
      setOf(
        "application/pdf",
        "application/json",
        "application/zip",
        "application/octet-stream",
      )
  }
}

data class WorkItemAttachmentUploadSession(
  val attachmentApiId: String,
  val presigned: PresignedBlobRequest,
)
