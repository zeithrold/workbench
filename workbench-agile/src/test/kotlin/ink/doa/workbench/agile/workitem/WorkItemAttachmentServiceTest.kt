package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.storage.BlobObjectHead
import ink.doa.workbench.core.storage.BlobStorage
import ink.doa.workbench.core.storage.BlobStorageObjectNotFoundException
import ink.doa.workbench.core.storage.PresignedBlobRequest
import ink.doa.workbench.core.storage.StorageLimits
import ink.doa.workbench.core.workitem.WorkItemAttachmentRepository
import ink.doa.workbench.core.workitem.model.AttachmentPurpose
import ink.doa.workbench.core.workitem.model.AttachmentUploadStatus
import ink.doa.workbench.core.workitem.model.CompleteWorkItemAttachmentUploadCommand
import ink.doa.workbench.core.workitem.model.DeleteWorkItemAttachmentCommand
import ink.doa.workbench.core.workitem.model.InitiateWorkItemAttachmentUploadCommand
import ink.doa.workbench.core.workitem.model.WorkItemAttachmentRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class WorkItemAttachmentServiceTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val issueId = UUID.randomUUID()
    val actorId = UUID.randomUUID()

    "initiate upload creates pending record and presigned put url" {
      val attachments = FakeWorkItemAttachmentRepository(issueId)
      val blobStorage = RecordingBlobStorage()
      val service = service(attachments, blobStorage)
      val command =
        InitiateWorkItemAttachmentUploadCommand(
          tenantId = tenantId,
          projectId = projectId,
          projectApiId = "prj_test",
          workItemApiId = "iss_test",
          uploadedBy = actorId,
          filename = "notes.txt",
          contentType = "text/plain",
          declaredByteSize = 5,
          purpose = AttachmentPurpose.STANDALONE,
        )

      val session = service.initiateUpload(command)

      session.attachmentApiId.shouldNotBeNull()
      session.presigned.method shouldBe "PUT"
      blobStorage.lastKey.shouldNotBeNull()
      blobStorage.lastContentType shouldBe "text/plain"
      blobStorage.lastContentLength shouldBe 5L
      attachments.lastPending?.uploadStatus shouldBe AttachmentUploadStatus.PENDING
    }

    "initiate upload rejects empty declared size" {
      val service = service(mockk(relaxed = true), mockk(relaxed = true))
      shouldThrow<InvalidRequestException> {
        service.initiateUpload(
          InitiateWorkItemAttachmentUploadCommand(
            tenantId = tenantId,
            projectId = projectId,
            projectApiId = "prj_test",
            workItemApiId = "iss_test",
            uploadedBy = actorId,
            filename = "empty.txt",
            contentType = "text/plain",
            declaredByteSize = 0,
            purpose = AttachmentPurpose.STANDALONE,
          )
        )
      }
    }

    "initiate upload requires comment id for comment attachments" {
      val service = service(mockk(relaxed = true), mockk(relaxed = true))
      shouldThrow<InvalidRequestException> {
        service.initiateUpload(
          InitiateWorkItemAttachmentUploadCommand(
            tenantId = tenantId,
            projectId = projectId,
            projectApiId = "prj_test",
            workItemApiId = "iss_test",
            uploadedBy = actorId,
            filename = "notes.txt",
            contentType = "text/plain",
            declaredByteSize = 5,
            purpose = AttachmentPurpose.COMMENT,
          )
        )
      }
    }

    "complete upload validates object head and marks attachment completed" {
      val attachments = FakeWorkItemAttachmentRepository(issueId)
      val blobStorage = RecordingBlobStorage()
      val service = service(attachments, blobStorage)
      val session =
        service.initiateUpload(
          InitiateWorkItemAttachmentUploadCommand(
            tenantId = tenantId,
            projectId = projectId,
            projectApiId = "prj_test",
            workItemApiId = "iss_test",
            uploadedBy = actorId,
            filename = "notes.txt",
            contentType = "text/plain",
            declaredByteSize = 5,
            purpose = AttachmentPurpose.STANDALONE,
          )
        )
      blobStorage.heads[attachments.lastPending!!.storageKey] =
        BlobObjectHead(contentType = "text/plain", contentLength = 5, etag = "\"abc123\"")

      val completed =
        service.completeUpload(
          CompleteWorkItemAttachmentUploadCommand(
            tenantId = tenantId,
            projectId = projectId,
            workItemApiId = "iss_test",
            attachmentApiId = session.attachmentApiId,
            uploadedBy = actorId,
          )
        )

      completed.uploadStatus shouldBe AttachmentUploadStatus.COMPLETED
      completed.checksum shouldBe "abc123"
    }

    "list and delete attachments" {
      val attachments = FakeWorkItemAttachmentRepository(issueId)
      val blobStorage = RecordingBlobStorage()
      val service = service(attachments, blobStorage)
      val session =
        service.initiateUpload(
          InitiateWorkItemAttachmentUploadCommand(
            tenantId = tenantId,
            projectId = projectId,
            projectApiId = "prj_test",
            workItemApiId = "iss_test",
            uploadedBy = actorId,
            filename = "notes.txt",
            contentType = "text/plain",
            declaredByteSize = 5,
            purpose = AttachmentPurpose.STANDALONE,
          )
        )
      blobStorage.heads[attachments.lastPending!!.storageKey] =
        BlobObjectHead(contentType = "text/plain", contentLength = 5, etag = "\"abc123\"")
      val completed =
        service.completeUpload(
          CompleteWorkItemAttachmentUploadCommand(
            tenantId = tenantId,
            projectId = projectId,
            workItemApiId = "iss_test",
            attachmentApiId = session.attachmentApiId,
            uploadedBy = actorId,
          )
        )

      service.list(tenantId, projectId, "iss_test", null, null).single().apiId shouldBe
        completed.apiId
      service.get(tenantId, projectId, "iss_test", completed.apiId.value).apiId shouldBe
        completed.apiId
      service.delete(
        DeleteWorkItemAttachmentCommand(
          tenantId = tenantId,
          projectId = projectId,
          workItemApiId = "iss_test",
          attachmentApiId = completed.apiId.value,
          actorUserId = actorId,
        )
      )
      blobStorage.deletedKeys.contains(completed.storageKey) shouldBe true
    }

    "initiate upload rejects unsupported content types" {
      val service = service(mockk(relaxed = true), mockk(relaxed = true))
      shouldThrow<InvalidRequestException> {
        service.initiateUpload(
          InitiateWorkItemAttachmentUploadCommand(
            tenantId = tenantId,
            projectId = projectId,
            projectApiId = "prj_test",
            workItemApiId = "iss_test",
            uploadedBy = actorId,
            filename = "archive.exe",
            contentType = "application/x-msdownload",
            declaredByteSize = 5,
            purpose = AttachmentPurpose.STANDALONE,
          )
        )
      }
    }

    "initiate comment attachment resolves comment id" {
      val attachments = FakeWorkItemAttachmentRepository(issueId)
      attachments.commentId = UUID.randomUUID()
      val blobStorage = RecordingBlobStorage()
      val service = service(attachments, blobStorage)
      val session =
        service.initiateUpload(
          InitiateWorkItemAttachmentUploadCommand(
            tenantId = tenantId,
            projectId = projectId,
            projectApiId = "prj_test",
            workItemApiId = "iss_test",
            uploadedBy = actorId,
            filename = "shot.png",
            contentType = "image/png",
            declaredByteSize = 3,
            purpose = AttachmentPurpose.COMMENT,
            commentApiId = "icm_test",
          )
        )

      attachments.lastPending?.purpose shouldBe AttachmentPurpose.COMMENT
      session.presigned.url.shouldNotBeNull()
    }

    "contentUrl builds attachment content path" {
      val service = service(mockk(relaxed = true), mockk(relaxed = true))
      service.contentUrl("prj_test", "iss_test", "att_test") shouldBe
        "/api/projects/prj_test/work-items/iss_test/attachments/att_test/content"
    }

    "presignedDownloadUrl returns presigned get url" {
      val attachments = FakeWorkItemAttachmentRepository(issueId)
      val blobStorage = RecordingBlobStorage()
      val service = service(attachments, blobStorage)
      val session =
        service.initiateUpload(
          InitiateWorkItemAttachmentUploadCommand(
            tenantId = tenantId,
            projectId = projectId,
            projectApiId = "prj_test",
            workItemApiId = "iss_test",
            uploadedBy = actorId,
            filename = "notes.txt",
            contentType = "text/plain",
            declaredByteSize = 5,
            purpose = AttachmentPurpose.STANDALONE,
          )
        )
      blobStorage.heads[attachments.lastPending!!.storageKey] =
        BlobObjectHead(contentType = "text/plain", contentLength = 5, etag = "\"abc123\"")
      val completed =
        service.completeUpload(
          CompleteWorkItemAttachmentUploadCommand(
            tenantId = tenantId,
            projectId = projectId,
            workItemApiId = "iss_test",
            attachmentApiId = session.attachmentApiId,
            uploadedBy = actorId,
          )
        )

      val presigned =
        service.presignedDownloadUrl(
          tenantId = tenantId,
          projectId = projectId,
          workItemApiId = "iss_test",
          attachmentApiId = completed.apiId.value,
        )

      presigned.method shouldBe "GET"
      presigned.url shouldBe "https://example.test/get/${completed.storageKey}"
    }
  })

private fun service(
  attachments: WorkItemAttachmentRepository,
  blobStorage: BlobStorage,
): WorkItemAttachmentService =
  WorkItemAttachmentService(
    attachments = attachments,
    blobStorage = blobStorage,
    storageLimits =
      StorageLimits(
        maxFileSizeBytes = 25L * 1024 * 1024,
        presignUploadTtl = Duration.ofMinutes(15),
      ),
  )

private class FakeWorkItemAttachmentRepository(private val issueId: UUID) :
  WorkItemAttachmentRepository {
  var lastPending: WorkItemAttachmentRecord? = null
  private var lastCompleted: WorkItemAttachmentRecord? = null
  var commentId: UUID? = null

  override suspend fun listByWorkItem(
    tenantId: UUID,
    issueId: UUID,
    purpose: AttachmentPurpose?,
    commentApiId: String?,
    limit: Int,
    offset: Long,
  ): List<WorkItemAttachmentRecord> =
    lastCompleted
      ?.let { listOf(it) }
      ?.filter {
        (purpose == null || it.purpose == purpose) &&
          (commentApiId == null || it.commentApiId?.value == commentApiId)
      } ?: emptyList()

  override suspend fun createPending(
    command: InitiateWorkItemAttachmentUploadCommand,
    issueId: UUID,
    commentId: UUID?,
    attachmentId: UUID,
    apiId: PublicId,
    storageKey: String,
  ): WorkItemAttachmentRecord =
    WorkItemAttachmentRecord(
        id = attachmentId,
        apiId = apiId,
        tenantId = command.tenantId,
        issueId = issueId,
        commentId = commentId,
        commentApiId = null,
        uploadedBy = command.uploadedBy,
        uploadedByApiId = PublicId.new("usr"),
        filename = command.filename,
        contentType = command.contentType,
        byteSize = command.declaredByteSize,
        checksum = null,
        storageKey = storageKey,
        purpose = command.purpose,
        uploadStatus = AttachmentUploadStatus.PENDING,
        createdAt = OffsetDateTime.now(ZoneOffset.UTC),
      )
      .also { lastPending = it }

  override suspend fun completePending(
    tenantId: UUID,
    issueId: UUID,
    attachmentApiId: String,
    uploadedBy: UUID,
    byteSize: Long,
    checksum: String,
  ): WorkItemAttachmentRecord {
    val pending = lastPending ?: error("missing pending attachment")
    return pending
      .copy(
        byteSize = byteSize,
        checksum = checksum,
        uploadStatus = AttachmentUploadStatus.COMPLETED,
      )
      .also {
        lastPending = null
        lastCompleted = it
      }
  }

  override suspend fun findByApiId(
    tenantId: UUID,
    issueId: UUID,
    attachmentApiId: String,
  ): WorkItemAttachmentRecord? = lastCompleted?.takeIf { it.apiId.value == attachmentApiId }

  override suspend fun findPendingByApiId(
    tenantId: UUID,
    issueId: UUID,
    attachmentApiId: String,
    uploadedBy: UUID,
  ): WorkItemAttachmentRecord? = lastPending?.takeIf {
    it.apiId.value == attachmentApiId && it.uploadedBy == uploadedBy
  }

  override suspend fun softDelete(
    command: DeleteWorkItemAttachmentCommand,
    issueId: UUID,
  ): WorkItemAttachmentRecord {
    val current = lastCompleted ?: error("missing attachment")
    lastCompleted = null
    return current
  }

  override suspend fun resolveIssueId(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
  ): UUID? = issueId

  override suspend fun resolveCommentId(
    tenantId: UUID,
    issueId: UUID,
    commentApiId: String,
  ): UUID? = commentId
}

private class RecordingBlobStorage : BlobStorage {
  var lastKey: String? = null
  var lastContentType: String? = null
  var lastContentLength: Long? = null
  val heads = mutableMapOf<String, BlobObjectHead>()
  val deletedKeys = mutableSetOf<String>()

  override suspend fun presignPut(
    key: String,
    contentType: String?,
    contentLength: Long,
  ): PresignedBlobRequest {
    lastKey = key
    lastContentType = contentType
    lastContentLength = contentLength
    return PresignedBlobRequest(
      url = "https://example.test/put/$key",
      method = "PUT",
      expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15),
      headers =
        buildMap {
          contentType?.let { put("Content-Type", it) }
        },
    )
  }

  override suspend fun presignGet(key: String): PresignedBlobRequest {
    if (!heads.containsKey(key)) {
      throw BlobStorageObjectNotFoundException(key)
    }
    return PresignedBlobRequest(
      url = "https://example.test/get/$key",
      method = "GET",
      expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15),
    )
  }

  override suspend fun head(key: String): BlobObjectHead =
    heads[key] ?: throw BlobStorageObjectNotFoundException(key)

  override suspend fun delete(key: String) {
    deletedKeys += key
    heads.remove(key)
  }
}
