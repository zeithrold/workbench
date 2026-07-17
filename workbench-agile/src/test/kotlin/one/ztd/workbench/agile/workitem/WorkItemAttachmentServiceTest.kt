package one.ztd.workbench.agile.workitem

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.agile.workitem.model.AttachmentPurpose
import one.ztd.workbench.agile.workitem.model.AttachmentUploadStatus
import one.ztd.workbench.agile.workitem.model.CompletePendingAttachmentCommand
import one.ztd.workbench.agile.workitem.model.CompleteWorkItemAttachmentUploadCommand
import one.ztd.workbench.agile.workitem.model.CreatePendingAttachmentCommand
import one.ztd.workbench.agile.workitem.model.DeleteWorkItemAttachmentCommand
import one.ztd.workbench.agile.workitem.model.InitiateWorkItemAttachmentUploadCommand
import one.ztd.workbench.agile.workitem.model.ListWorkItemAttachmentsQuery
import one.ztd.workbench.agile.workitem.model.WorkItemAttachmentRecord
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.kernel.storage.BlobObjectHead
import one.ztd.workbench.kernel.storage.BlobStorage
import one.ztd.workbench.kernel.storage.BlobStorageObjectNotFoundException
import one.ztd.workbench.kernel.storage.PresignedBlobRequest
import one.ztd.workbench.kernel.storage.StorageLimits

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

      service
        .list(
          ListWorkItemAttachmentsRequest(
            tenantId = tenantId,
            projectId = projectId,
            workItemApiId = "iss_test",
          )
        )
        .single()
        .apiId shouldBe completed.apiId
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

    "initiate upload rejects file exceeding max size" {
      val service =
        service(
          mockk(relaxed = true),
          mockk(relaxed = true),
          maxFileSizeBytes = 10,
        )
      shouldThrow<InvalidRequestException> {
        service.initiateUpload(
          InitiateWorkItemAttachmentUploadCommand(
            tenantId = tenantId,
            projectId = projectId,
            projectApiId = "prj_test",
            workItemApiId = "iss_test",
            uploadedBy = actorId,
            filename = "large.bin",
            contentType = "application/octet-stream",
            declaredByteSize = 11,
            purpose = AttachmentPurpose.STANDALONE,
          )
        )
      }
    }

    "initiate upload allows null content type" {
      val attachments = FakeWorkItemAttachmentRepository(issueId)
      val blobStorage = RecordingBlobStorage()
      val service = service(attachments, blobStorage)

      service.initiateUpload(
        InitiateWorkItemAttachmentUploadCommand(
          tenantId = tenantId,
          projectId = projectId,
          projectApiId = "prj_test",
          workItemApiId = "iss_test",
          uploadedBy = actorId,
          filename = "notes.bin",
          contentType = null,
          declaredByteSize = 5,
          purpose = AttachmentPurpose.STANDALONE,
        )
      )

      blobStorage.lastContentType shouldBe null
    }

    "rejects upload when work item is not found" {
      val attachments = FakeWorkItemAttachmentRepository(issueId)
      attachments.issueResolvable = false
      val service = service(attachments, RecordingBlobStorage())

      shouldThrow<ResourceNotFoundException> {
        service.initiateUpload(
          InitiateWorkItemAttachmentUploadCommand(
            tenantId = tenantId,
            projectId = projectId,
            projectApiId = "prj_test",
            workItemApiId = "iss_missing",
            uploadedBy = actorId,
            filename = "notes.txt",
            contentType = "text/plain",
            declaredByteSize = 5,
            purpose = AttachmentPurpose.STANDALONE,
          )
        )
      }
    }

    "rejects comment attachment when comment is not found" {
      val attachments = FakeWorkItemAttachmentRepository(issueId)
      attachments.commentId = null
      val service = service(attachments, RecordingBlobStorage())

      shouldThrow<ResourceNotFoundException> {
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
            commentApiId = "icm_missing",
          )
        )
      }
    }

    "complete upload rejects expired pending upload" {
      val attachments = FakeWorkItemAttachmentRepository(issueId)
      attachments.pendingCreatedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)
      val blobStorage = RecordingBlobStorage()
      val service =
        service(
          attachments,
          blobStorage,
          presignUploadTtl = Duration.ofMinutes(15),
        )
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

      shouldThrow<InvalidRequestException> {
        service.completeUpload(
          CompleteWorkItemAttachmentUploadCommand(
            tenantId = tenantId,
            projectId = projectId,
            workItemApiId = "iss_test",
            attachmentApiId = session.attachmentApiId,
            uploadedBy = actorId,
          )
        )
      }
    }

    "complete upload rejects incomplete blob object" {
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

      shouldThrow<InvalidRequestException> {
        service.completeUpload(
          CompleteWorkItemAttachmentUploadCommand(
            tenantId = tenantId,
            projectId = projectId,
            workItemApiId = "iss_test",
            attachmentApiId = session.attachmentApiId,
            uploadedBy = actorId,
          )
        )
      }
    }

    "complete upload rejects size mismatch and deletes blob" {
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
      val storageKey = attachments.lastPending!!.storageKey
      blobStorage.heads[storageKey] =
        BlobObjectHead(contentType = "text/plain", contentLength = 9, etag = "\"abc123\"")

      shouldThrow<InvalidRequestException> {
        service.completeUpload(
          CompleteWorkItemAttachmentUploadCommand(
            tenantId = tenantId,
            projectId = projectId,
            workItemApiId = "iss_test",
            attachmentApiId = session.attachmentApiId,
            uploadedBy = actorId,
          )
        )
      }
      blobStorage.deletedKeys.contains(storageKey) shouldBe true
    }

    "complete upload uses unknown checksum when etag is missing" {
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
        BlobObjectHead(contentType = "text/plain", contentLength = 5, etag = null)

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

      completed.checksum shouldBe "unknown"
    }

    "get throws when attachment is not found" {
      val service = service(FakeWorkItemAttachmentRepository(issueId), RecordingBlobStorage())

      shouldThrow<ResourceNotFoundException> {
        service.get(tenantId, projectId, "iss_test", "att_missing")
      }
    }

    "presignedDownloadUrl throws when blob object is missing" {
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
      blobStorage.presignGetThrows += completed.storageKey

      shouldThrow<ResourceNotFoundException> {
        service.presignedDownloadUrl(
          tenantId = tenantId,
          projectId = projectId,
          workItemApiId = "iss_test",
          attachmentApiId = completed.apiId.value,
        )
      }
    }

    "contentRedirectUrl returns presigned download url" {
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

      service.contentRedirectUrl(
        tenantId = tenantId,
        projectId = projectId,
        workItemApiId = "iss_test",
        attachmentApiId = completed.apiId.value,
      ) shouldBe "https://example.test/get/${completed.storageKey}"
    }

    "complete upload rejects missing pending attachment" {
      val attachments = FakeWorkItemAttachmentRepository(issueId)
      val service = service(attachments, RecordingBlobStorage())

      shouldThrow<ResourceNotFoundException> {
        service.completeUpload(
          CompleteWorkItemAttachmentUploadCommand(
            tenantId = tenantId,
            projectId = projectId,
            workItemApiId = "iss_test",
            attachmentApiId = "att_missing",
            uploadedBy = actorId,
          )
        )
      }
    }
  })

private fun service(
  attachments: WorkItemAttachmentRepository,
  blobStorage: BlobStorage,
  maxFileSizeBytes: Long = 25L * 1024 * 1024,
  presignUploadTtl: Duration = Duration.ofMinutes(15),
): WorkItemAttachmentService =
  WorkItemAttachmentService(
    attachments = attachments,
    blobStorage = blobStorage,
    storageLimits =
      StorageLimits(
        maxFileSizeBytes = maxFileSizeBytes,
        presignUploadTtl = presignUploadTtl,
      ),
  )

private class FakeWorkItemAttachmentRepository(private val issueId: UUID) :
  WorkItemAttachmentRepository {
  var lastPending: WorkItemAttachmentRecord? = null
  private var lastCompleted: WorkItemAttachmentRecord? = null
  var commentId: UUID? = null
  var issueResolvable = true
  var pendingCreatedAt: OffsetDateTime? = null

  override suspend fun listByWorkItem(
    query: ListWorkItemAttachmentsQuery
  ): List<WorkItemAttachmentRecord> =
    lastCompleted
      ?.let { listOf(it) }
      ?.filter {
        (query.purpose == null || it.purpose == query.purpose) &&
          (query.commentApiId == null || it.commentApiId?.value == query.commentApiId)
      }
      .orEmpty()

  override suspend fun createPending(
    command: CreatePendingAttachmentCommand
  ): WorkItemAttachmentRecord =
    WorkItemAttachmentRecord(
        id = command.attachmentId,
        apiId = command.apiId,
        tenantId = command.upload.tenantId,
        issueId = command.issueId,
        commentId = command.commentId,
        commentApiId = null,
        uploadedBy = command.upload.uploadedBy,
        uploadedByApiId = PublicId.new("usr"),
        filename = command.upload.filename,
        contentType = command.upload.contentType,
        byteSize = command.upload.declaredByteSize,
        checksum = null,
        storageKey = command.storageKey,
        purpose = command.upload.purpose,
        uploadStatus = AttachmentUploadStatus.PENDING,
        createdAt = pendingCreatedAt ?: OffsetDateTime.now(ZoneOffset.UTC),
      )
      .also { lastPending = it }

  override suspend fun completePending(
    command: CompletePendingAttachmentCommand
  ): WorkItemAttachmentRecord {
    val pending = lastPending ?: error("missing pending attachment")
    return pending
      .copy(
        byteSize = command.byteSize,
        checksum = command.checksum,
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
  ): UUID? = issueId.takeIf { issueResolvable }

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
  val presignGetThrows = mutableSetOf<String>()

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
    if (key in presignGetThrows || !heads.containsKey(key)) {
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
