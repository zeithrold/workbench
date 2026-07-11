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
import ink.doa.workbench.core.workitem.model.CompletePendingAttachmentCommand
import ink.doa.workbench.core.workitem.model.CompleteWorkItemAttachmentUploadCommand
import ink.doa.workbench.core.workitem.model.CreatePendingAttachmentCommand
import ink.doa.workbench.core.workitem.model.DeleteWorkItemAttachmentCommand
import ink.doa.workbench.core.workitem.model.InitiateWorkItemAttachmentUploadCommand
import ink.doa.workbench.core.workitem.model.ListWorkItemAttachmentsQuery
import ink.doa.workbench.core.workitem.model.WorkItemAttachmentRecord
import ink.doa.workbench.core.workitem.richtext.AttachmentContentUrl
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.stereotype.Service

data class ListWorkItemAttachmentsRequest(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemApiId: String,
  val purpose: AttachmentPurpose? = null,
  val commentApiId: String? = null,
  val limit: Int = 50,
  val offset: Long = 0,
)

private val allowedContentTypePrefixes = listOf("image/", "text/")
private val allowedContentTypes =
  setOf("application/pdf", "application/json", "application/zip", "application/octet-stream")

@Service
class WorkItemAttachmentService(
  private val attachments: WorkItemAttachmentRepository,
  private val blobStorage: BlobStorage,
  private val storageLimits: StorageLimits,
) {
  suspend fun list(request: ListWorkItemAttachmentsRequest): List<WorkItemAttachmentRecord> {
    val issueId = requireIssueId(request.tenantId, request.projectId, request.workItemApiId)
    return attachments.listByWorkItem(
      ListWorkItemAttachmentsQuery(
        tenantId = request.tenantId,
        issueId = issueId,
        purpose = request.purpose,
        commentApiId = request.commentApiId,
        limit = request.limit,
        offset = request.offset,
      )
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
    uploadValidationError(command, storageLimits)?.let { throw InvalidRequestException(it) }
    val issueId = requireIssueId(command.tenantId, command.projectId, command.workItemApiId)
    val commentId = resolveCommentId(command, issueId)
    val attachmentId = UUID.randomUUID()
    val apiId = PublicId.new("att")
    val storageKey = buildStorageKey(command.tenantId, issueId, attachmentId, command.filename)
    val pending =
      attachments.createPending(
        CreatePendingAttachmentCommand(
          upload = command,
          issueId = issueId,
          commentId = commentId,
          attachmentId = attachmentId,
          apiId = apiId,
          storageKey = storageKey,
        )
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
    val validationError = completeUploadValidationError(pending, headResult, storageLimits)
    if (validationError != null) {
      if (validationError == WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_UPLOAD_SIZE_MISMATCH) {
        runCatching { blobStorage.delete(pending.storageKey) }
      }
      throw InvalidRequestException(validationError)
    }
    val head = headResult.getOrThrow()
    return attachments.completePending(
      CompletePendingAttachmentCommand(
        tenantId = command.tenantId,
        issueId = issueId,
        attachmentApiId = command.attachmentApiId,
        uploadedBy = command.uploadedBy,
        byteSize = head.contentLength,
        checksum = normalizeChecksum(head.etag),
      )
    )
  }

  private suspend fun resolveUploadHead(pending: WorkItemAttachmentRecord): Result<BlobObjectHead> =
    runCatching {
      blobStorage.head(pending.storageKey)
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
  ): String = AttachmentContentUrl.build(projectApiId, workItemApiId, attachmentApiId)

  private suspend fun resolveCommentId(
    command: InitiateWorkItemAttachmentUploadCommand,
    issueId: UUID,
  ): UUID? {
    if (command.purpose != AttachmentPurpose.COMMENT) return null
    val commentApiId =
      command.commentApiId
        ?: throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_COMMENT_REQUIRED)
    return attachments.resolveCommentId(command.tenantId, issueId, commentApiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_COMMENT_NOT_FOUND)
  }

  private suspend fun requireIssueId(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
  ): UUID =
    attachments.resolveIssueId(tenantId, projectId, workItemApiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)
}

private fun completeUploadValidationError(
  pending: WorkItemAttachmentRecord,
  headResult: Result<BlobObjectHead>,
  limits: StorageLimits,
): WorkbenchErrorCode? =
  when {
    isUploadExpired(pending.createdAt, limits) ->
      WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_UPLOAD_EXPIRED
    headResult.isFailure -> WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_UPLOAD_INCOMPLETE
    headResult.getOrThrow().contentLength != pending.byteSize ->
      WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_UPLOAD_SIZE_MISMATCH
    else -> null
  }

private fun uploadValidationError(
  command: InitiateWorkItemAttachmentUploadCommand,
  limits: StorageLimits,
): WorkbenchErrorCode? =
  when {
    command.declaredByteSize <= 0L -> WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_FILE_REQUIRED
    command.declaredByteSize > limits.maxFileSizeBytes ->
      WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_TOO_LARGE
    command.purpose == AttachmentPurpose.COMMENT && command.commentApiId.isNullOrBlank() ->
      WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_COMMENT_REQUIRED
    !isAllowedContentType(command.contentType) ->
      WorkbenchErrorCode.WORK_ITEM_ATTACHMENT_CONTENT_TYPE_UNSUPPORTED
    else -> null
  }

private fun isUploadExpired(createdAt: OffsetDateTime, limits: StorageLimits): Boolean =
  OffsetDateTime.now(ZoneOffset.UTC).isAfter(createdAt.plus(limits.presignUploadTtl))

private fun isAllowedContentType(contentType: String?): Boolean {
  if (contentType == null) return true
  val normalized = contentType.substringBefore(';').trim().lowercase()
  return allowedContentTypePrefixes.any(normalized::startsWith) || normalized in allowedContentTypes
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

data class WorkItemAttachmentUploadSession(
  val attachmentApiId: String,
  val presigned: PresignedBlobRequest,
)
