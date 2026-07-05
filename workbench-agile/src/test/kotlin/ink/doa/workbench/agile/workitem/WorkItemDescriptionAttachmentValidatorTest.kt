package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.project.ProjectRepository
import ink.doa.workbench.core.project.model.ProjectRecord
import ink.doa.workbench.core.project.model.ProjectStatus
import ink.doa.workbench.core.workitem.WorkItemAttachmentRepository
import ink.doa.workbench.core.workitem.model.AttachmentPurpose
import ink.doa.workbench.core.workitem.model.AttachmentUploadStatus
import ink.doa.workbench.core.workitem.model.WorkItemAttachmentRecord
import ink.doa.workbench.core.workitem.richtext.AttachmentReferenceParser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class WorkItemDescriptionAttachmentValidatorTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val issueId = UUID.randomUUID()
    val projectApiId = PublicId.new("prj")
    val workItemApiId = PublicId.new("iss")
    val attachmentApiId = PublicId.new("att")

    "rejectCreateDescriptionReferences rejects html with attachment images" {
      val validator = validator(mockk(relaxed = true), mockk(relaxed = true))
      shouldThrow<InvalidRequestException> {
        validator.rejectCreateDescriptionReferences(
          """<img src="${AttachmentReferenceParser.buildContentUrl(projectApiId.value, workItemApiId.value, attachmentApiId.value)}">"""
        )
      }
    }

    "validateReferences accepts description attachment references" {
      val attachments = mockk<WorkItemAttachmentRepository>()
      val projects = mockk<ProjectRepository>()
      coEvery { projects.findById(tenantId, projectId) } returns projectRecord(projectApiId)
      coEvery { attachments.findByApiId(tenantId, issueId, attachmentApiId.value) } returns
        attachmentRecord(attachmentApiId)
      val validator = validator(attachments, projects)

      validator.validateReferences(
        tenantId = tenantId,
        projectId = projectId,
        workItemApiId = workItemApiId.value,
        issueId = issueId,
        descriptionHtml =
          """<img src="${AttachmentReferenceParser.buildContentUrl(projectApiId.value, workItemApiId.value, attachmentApiId.value)}">""",
      )
    }

    "validateReferences rejects standalone attachment references in description" {
      val attachments = mockk<WorkItemAttachmentRepository>()
      val projects = mockk<ProjectRepository>()
      coEvery { projects.findById(tenantId, projectId) } returns projectRecord(projectApiId)
      coEvery { attachments.findByApiId(tenantId, issueId, attachmentApiId.value) } returns
        attachmentRecord(attachmentApiId, AttachmentPurpose.STANDALONE)
      val validator = validator(attachments, projects)

      shouldThrow<InvalidRequestException> {
        validator.validateReferences(
          tenantId = tenantId,
          projectId = projectId,
          workItemApiId = workItemApiId.value,
          issueId = issueId,
          descriptionHtml =
            """<img src="${AttachmentReferenceParser.buildContentUrl(projectApiId.value, workItemApiId.value, attachmentApiId.value)}">""",
        )
      }
    }

    "validateReferences rejects references to another work item" {
      val attachments = mockk<WorkItemAttachmentRepository>()
      val projects = mockk<ProjectRepository>()
      coEvery { projects.findById(tenantId, projectId) } returns projectRecord(projectApiId)
      val validator = validator(attachments, projects)

      val otherWorkItemApiId = PublicId.new("iss").value
      val invalidHtml =
        """<img src="${AttachmentReferenceParser.buildContentUrl(
          projectApiId.value,
          otherWorkItemApiId,
          attachmentApiId.value,
        )}">"""
      shouldThrow<InvalidRequestException> {
        validator.validateReferences(
          tenantId = tenantId,
          projectId = projectId,
          workItemApiId = workItemApiId.value,
          issueId = issueId,
          descriptionHtml = invalidHtml,
        )
      }
    }
  })

private fun validator(
  attachments: WorkItemAttachmentRepository,
  projects: ProjectRepository,
): WorkItemDescriptionAttachmentValidator =
  WorkItemDescriptionAttachmentValidator(attachments, projects)

private fun projectRecord(projectApiId: PublicId): ProjectRecord =
  ProjectRecord(
    id = UUID.randomUUID(),
    apiId = projectApiId,
    tenantId = UUID.randomUUID(),
    identifier = "WB",
    name = "Workbench",
    description = null,
    status = ProjectStatus.ACTIVE,
  )

private fun attachmentRecord(
  apiId: PublicId,
  purpose: AttachmentPurpose = AttachmentPurpose.DESCRIPTION,
): WorkItemAttachmentRecord =
  WorkItemAttachmentRecord(
    id = UUID.randomUUID(),
    apiId = apiId,
    tenantId = UUID.randomUUID(),
    issueId = UUID.randomUUID(),
    commentId = null,
    commentApiId = null,
    uploadedBy = UUID.randomUUID(),
    uploadedByApiId = PublicId.new("usr"),
    filename = "diagram.png",
    contentType = "image/png",
    byteSize = 3,
    checksum = "abc",
    storageKey = "key",
    purpose = purpose,
    uploadStatus = AttachmentUploadStatus.COMPLETED,
    createdAt = OffsetDateTime.now(ZoneOffset.UTC),
  )
