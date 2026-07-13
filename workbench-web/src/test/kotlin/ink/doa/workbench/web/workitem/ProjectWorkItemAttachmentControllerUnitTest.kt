package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.workitem.WorkItemAttachmentService
import ink.doa.workbench.agile.workitem.WorkItemAttachmentUploadSession
import ink.doa.workbench.agile.workitem.model.AttachmentPurpose
import ink.doa.workbench.agile.workitem.model.AttachmentUploadStatus
import ink.doa.workbench.agile.workitem.model.WorkItemAttachmentRecord
import ink.doa.workbench.kernel.common.context.InstanceContextSummary
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.kernel.storage.PresignedBlobRequest
import ink.doa.workbench.web.api.context.ApiVersion
import ink.doa.workbench.web.api.context.ProjectContextSummary
import ink.doa.workbench.web.api.context.ProjectRequestContext
import ink.doa.workbench.web.api.context.TenantContextSummary
import ink.doa.workbench.web.api.context.UserContextSummary
import ink.doa.workbench.web.support.TenantWebMvcFixtures
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus

class ProjectWorkItemAttachmentControllerUnitTest :
  StringSpec({
    val service = mockk<WorkItemAttachmentService>()
    val controller = ProjectWorkItemAttachmentController(service)
    val projectContext =
      ProjectRequestContext(
        requestId = "req",
        apiVersion = ApiVersion.Default,
        actor =
          UserContextSummary(
            id = TenantWebMvcFixtures.USER_ID,
            publicId = TenantWebMvcFixtures.PRINCIPAL.user.apiId,
            displayName = TenantWebMvcFixtures.PRINCIPAL.user.displayName,
            primaryEmail = TenantWebMvcFixtures.PRINCIPAL.user.primaryEmail,
          ),
        receivedAt = Instant.parse("2026-07-04T00:00:00Z"),
        instance = InstanceContextSummary(id = "default", name = "Default"),
        tenant =
          TenantContextSummary(
            id = TenantWebMvcFixtures.TENANT_ID,
            publicId = TenantWebMvcFixtures.TENANT_RECORD.apiId,
            slug = TenantWebMvcFixtures.TENANT_RECORD.slug,
            name = TenantWebMvcFixtures.TENANT_RECORD.name,
          ),
        project =
          ProjectContextSummary(
            id = TenantWebMvcFixtures.PROJECT_ID,
            publicId = TenantWebMvcFixtures.PROJECT_RECORD.apiId,
            identifier = TenantWebMvcFixtures.PROJECT_RECORD.identifier,
            name = TenantWebMvcFixtures.PROJECT_RECORD.name,
          ),
      )
    val workItemId = "iss_test"
    val attachment =
      WorkItemAttachmentRecord(
        id = UUID.randomUUID(),
        apiId = PublicId("att_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        tenantId = TenantWebMvcFixtures.TENANT_ID,
        issueId = UUID.randomUUID(),
        commentId = null,
        commentApiId = null,
        uploadedBy = TenantWebMvcFixtures.USER_ID,
        uploadedByApiId = TenantWebMvcFixtures.PRINCIPAL.user.apiId,
        filename = "notes.txt",
        contentType = "text/plain",
        byteSize = 5,
        checksum = "abc",
        storageKey = "key",
        purpose = AttachmentPurpose.STANDALONE,
        uploadStatus = AttachmentUploadStatus.COMPLETED,
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )

    "create upload session returns presigned upload response" {
      coEvery { service.initiateUpload(any()) } returns
        WorkItemAttachmentUploadSession(
          attachmentApiId = attachment.apiId.value,
          presigned =
            PresignedBlobRequest(
              url = "https://minio.test/upload/key",
              method = "PUT",
              expiresAt = OffsetDateTime.parse("2026-07-04T00:15:00Z"),
              headers = mapOf("Content-Type" to "text/plain"),
            ),
        )

      val response = runBlocking {
        controller.createUploadSession(
          workItemId = workItemId,
          request =
            InitiateWorkItemAttachmentUploadRequest(
              filename = "notes.txt",
              contentType = "text/plain",
              byteSize = 5,
            ),
          projectContext = projectContext,
        )
      }

      response.statusCode.value() shouldBe 201
      response.body?.id shouldBe attachment.apiId.value
      response.body?.uploadUrl shouldBe "https://minio.test/upload/key"
      response.body?.uploadMethod shouldBe "PUT"
    }

    "complete upload session returns attachment with presigned download url" {
      coEvery { service.completeUpload(any()) } returns attachment
      coEvery { service.presignedDownloadUrl(any(), any(), any(), any()) } returns
        PresignedBlobRequest(
          url = "https://minio.test/download/key",
          method = "GET",
          expiresAt = OffsetDateTime.parse("2026-07-04T00:15:00Z"),
        )

      val response = runBlocking {
        controller.completeUploadSession(
          workItemId = workItemId,
          sessionId = attachment.apiId.value,
          projectContext = projectContext,
        )
      }

      response.id shouldBe attachment.apiId.value
      response.downloadUrl shouldBe "https://minio.test/download/key"
    }

    "download content redirects to presigned url" {
      coEvery { service.contentRedirectUrl(any(), any(), any(), any()) } returns
        "https://minio.test/download/key"

      val response = runBlocking {
        controller.downloadContent(
          workItemId = workItemId,
          attachmentId = attachment.apiId.value,
          projectContext = projectContext,
        )
      }

      response.statusCode shouldBe HttpStatus.FOUND
      response.headers.location.toString() shouldContain "https://minio.test/download/key"
    }
  })
