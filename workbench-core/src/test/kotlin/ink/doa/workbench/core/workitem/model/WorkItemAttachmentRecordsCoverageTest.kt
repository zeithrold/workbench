package ink.doa.workbench.core.workitem.model

import ink.doa.workbench.core.common.ids.PublicId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class WorkItemAttachmentRecordsCoverageTest :
  StringSpec({
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val tenantId = UUID.randomUUID()
    val issueId = UUID.randomUUID()
    val uploadedBy = UUID.randomUUID()

    "attachment purpose resolves wire values" {
      AttachmentPurpose.fromWire("standalone") shouldBe AttachmentPurpose.STANDALONE
      AttachmentPurpose.fromWire("description") shouldBe AttachmentPurpose.DESCRIPTION
      AttachmentPurpose.fromWire("comment") shouldBe AttachmentPurpose.COMMENT
      AttachmentPurpose.fromWire("missing").shouldBeNull()
    }

    "attachment upload status resolves wire values" {
      AttachmentUploadStatus.fromWire("pending") shouldBe AttachmentUploadStatus.PENDING
      AttachmentUploadStatus.fromWire("completed") shouldBe AttachmentUploadStatus.COMPLETED
      AttachmentUploadStatus.fromWire("missing").shouldBeNull()
    }

    "attachment record stores metadata" {
      val record =
        WorkItemAttachmentRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("att"),
          tenantId = tenantId,
          issueId = issueId,
          commentId = null,
          commentApiId = null,
          uploadedBy = uploadedBy,
          uploadedByApiId = PublicId.new("usr"),
          filename = "screenshot.png",
          contentType = "image/png",
          byteSize = 1024,
          checksum = "abc123",
          storageKey = "tenant/attachments/screenshot.png",
          purpose = AttachmentPurpose.STANDALONE,
          uploadStatus = AttachmentUploadStatus.COMPLETED,
          createdAt = now,
        )

      record.filename shouldBe "screenshot.png"
      record.purpose shouldBe AttachmentPurpose.STANDALONE
    }

    "attachment commands carry actor and scope" {
      InitiateWorkItemAttachmentUploadCommand(
          tenantId = tenantId,
          projectId = UUID.randomUUID(),
          projectApiId = "prj_test",
          workItemApiId = "iss_test",
          uploadedBy = uploadedBy,
          filename = "doc.pdf",
          contentType = "application/pdf",
          declaredByteSize = 2048,
          purpose = AttachmentPurpose.DESCRIPTION,
        )
        .declaredByteSize shouldBe 2048

      CompleteWorkItemAttachmentUploadCommand(
          tenantId = tenantId,
          projectId = UUID.randomUUID(),
          workItemApiId = "iss_test",
          attachmentApiId = "att_test",
          uploadedBy = uploadedBy,
        )
        .attachmentApiId shouldBe "att_test"

      DeleteWorkItemAttachmentCommand(
          tenantId = tenantId,
          projectId = UUID.randomUUID(),
          workItemApiId = "iss_test",
          attachmentApiId = "att_test",
          actorUserId = uploadedBy,
          deleteReason = "obsolete",
        )
        .deleteReason shouldBe "obsolete"

      ListWorkItemAttachmentsQuery(
          tenantId = tenantId,
          issueId = issueId,
          purpose = AttachmentPurpose.COMMENT,
          commentApiId = "cmt_test",
          limit = 25,
          offset = 0,
        )
        .purpose shouldBe AttachmentPurpose.COMMENT

      CreatePendingAttachmentCommand(
          upload =
            InitiateWorkItemAttachmentUploadCommand(
              tenantId = tenantId,
              projectId = UUID.randomUUID(),
              projectApiId = "prj_test",
              workItemApiId = "iss_test",
              uploadedBy = uploadedBy,
              filename = "pending.bin",
              contentType = null,
              declaredByteSize = 512,
              purpose = AttachmentPurpose.STANDALONE,
            ),
          issueId = issueId,
          commentId = null,
          attachmentId = UUID.randomUUID(),
          apiId = PublicId.new("att"),
          storageKey = "pending/key",
        )
        .storageKey shouldBe "pending/key"

      CompletePendingAttachmentCommand(
          tenantId = tenantId,
          issueId = issueId,
          attachmentApiId = "att_test",
          uploadedBy = uploadedBy,
          byteSize = 512,
          checksum = "deadbeef",
        )
        .checksum shouldBe "deadbeef"
    }
  })
