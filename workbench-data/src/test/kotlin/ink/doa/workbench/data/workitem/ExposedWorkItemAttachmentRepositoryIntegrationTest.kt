package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.AttachmentPurpose
import ink.doa.workbench.core.workitem.model.AttachmentUploadStatus
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommentCommand
import ink.doa.workbench.core.workitem.model.DeleteWorkItemAttachmentCommand
import ink.doa.workbench.core.workitem.model.InitiateWorkItemAttachmentUploadCommand
import ink.doa.workbench.data.project.ExposedProjectRepository
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.withPostgresDatabase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID
import org.junit.jupiter.api.Tag

@Tag("integration")
class ExposedWorkItemAttachmentRepositoryIntegrationTest :
  StringSpec({
    "create list filter and soft delete attachments" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val projects = ExposedProjectRepository(database)
        val project = projects.findById(stack.tenantId, stack.projectId) ?: error("project missing")
        val workItems = ExposedWorkItemRepository(database)
        val comments = ExposedWorkItemCommentRepository(database)
        val attachments = ExposedWorkItemAttachmentRepository(database)
        val created =
          workItems.create(
            CreateWorkItemCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              issueTypeApiId = stack.issueType.apiId.value,
              title = "Attachment target",
              description = null,
              reporterId = stack.actorId,
              actorUserId = stack.actorId,
            ),
            issueTypeId = stack.issueType.id,
            issueTypeConfigId = stack.config.config.id,
            initialStatusId = stack.todoStatus.id,
            propertyValues = emptyList(),
          )
        val issueId = created.workItem.id
        val workItemApiId = created.workItem.apiId.value
        val comment =
          comments.create(
            CreateWorkItemCommentCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              workItemApiId = workItemApiId,
              authorId = stack.actorId,
              body = "<p>With attachment</p>",
              bodyPlainText = "With attachment",
            ),
            issueId = issueId,
          )

        val standalone =
          createCompletedAttachment(
            attachments = attachments,
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            projectApiId = project.apiId.value,
            workItemApiId = workItemApiId,
            issueId = issueId,
            uploadedBy = stack.actorId,
            filename = "notes.txt",
            contentType = "text/plain",
            byteSize = 5,
            purpose = AttachmentPurpose.STANDALONE,
            commentId = null,
            storageKey = "tenant/issue/standalone/notes.txt",
            checksum = "abc",
          )
        val descriptionAttachment =
          createCompletedAttachment(
            attachments = attachments,
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            projectApiId = project.apiId.value,
            workItemApiId = workItemApiId,
            issueId = issueId,
            uploadedBy = stack.actorId,
            filename = "diagram.png",
            contentType = "image/png",
            byteSize = 3,
            purpose = AttachmentPurpose.DESCRIPTION,
            commentId = null,
            storageKey = "tenant/issue/description/diagram.png",
            checksum = "def",
          )
        val commentAttachment =
          createCompletedAttachment(
            attachments = attachments,
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            projectApiId = project.apiId.value,
            workItemApiId = workItemApiId,
            issueId = issueId,
            uploadedBy = stack.actorId,
            filename = "screenshot.png",
            contentType = "image/png",
            byteSize = 3,
            purpose = AttachmentPurpose.COMMENT,
            commentId = comment.id,
            commentApiId = comment.apiId.value,
            storageKey = "tenant/issue/comment/screenshot.png",
            checksum = "ghi",
          )

        attachments
          .listByWorkItem(stack.tenantId, issueId, purpose = null, commentApiId = null, 50, 0)
          .shouldHaveSize(3)
        attachments
          .listByWorkItem(
            stack.tenantId,
            issueId,
            purpose = AttachmentPurpose.STANDALONE,
            commentApiId = null,
            50,
            0,
          )
          .shouldHaveSize(1)
          .first()
          .apiId shouldBe standalone.apiId
        attachments
          .listByWorkItem(
            stack.tenantId,
            issueId,
            purpose = AttachmentPurpose.COMMENT,
            commentApiId = comment.apiId.value,
            50,
            0,
          )
          .shouldHaveSize(1)
          .first()
          .apiId shouldBe commentAttachment.apiId

        attachments
          .findByApiId(stack.tenantId, issueId, descriptionAttachment.apiId.value)
          ?.purpose shouldBe AttachmentPurpose.DESCRIPTION

        attachments.softDelete(
          DeleteWorkItemAttachmentCommand(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            workItemApiId = workItemApiId,
            attachmentApiId = standalone.apiId.value,
            actorUserId = stack.actorId,
          ),
          issueId = issueId,
        )
        attachments.findByApiId(stack.tenantId, issueId, standalone.apiId.value).shouldBeNull()
      }
    }

    "pending attachments are hidden until completed" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val projects = ExposedProjectRepository(database)
        val project = projects.findById(stack.tenantId, stack.projectId) ?: error("project missing")
        val workItems = ExposedWorkItemRepository(database)
        val attachments = ExposedWorkItemAttachmentRepository(database)
        val created =
          workItems.create(
            CreateWorkItemCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              issueTypeApiId = stack.issueType.apiId.value,
              title = "Pending attachment target",
              description = null,
              reporterId = stack.actorId,
              actorUserId = stack.actorId,
            ),
            issueTypeId = stack.issueType.id,
            issueTypeConfigId = stack.config.config.id,
            initialStatusId = stack.todoStatus.id,
            propertyValues = emptyList(),
          )
        val issueId = created.workItem.id
        val pending =
          attachments.createPending(
            command =
              InitiateWorkItemAttachmentUploadCommand(
                tenantId = stack.tenantId,
                projectId = stack.projectId,
                projectApiId = project.apiId.value,
                workItemApiId = created.workItem.apiId.value,
                uploadedBy = stack.actorId,
                filename = "pending.txt",
                contentType = "text/plain",
                declaredByteSize = 4,
                purpose = AttachmentPurpose.STANDALONE,
              ),
            issueId = issueId,
            commentId = null,
            attachmentId = UUID.randomUUID(),
            apiId = PublicId.new("att"),
            storageKey = "tenant/issue/pending/pending.txt",
          )

        pending.uploadStatus shouldBe AttachmentUploadStatus.PENDING
        attachments
          .listByWorkItem(stack.tenantId, issueId, purpose = null, commentApiId = null, 50, 0)
          .shouldHaveSize(0)
        attachments.findByApiId(stack.tenantId, issueId, pending.apiId.value).shouldBeNull()
        attachments
          .findPendingByApiId(
            tenantId = stack.tenantId,
            issueId = issueId,
            attachmentApiId = pending.apiId.value,
            uploadedBy = UUID.randomUUID(),
          )
          .shouldBeNull()

        val completed =
          attachments.completePending(
            tenantId = stack.tenantId,
            issueId = issueId,
            attachmentApiId = pending.apiId.value,
            uploadedBy = stack.actorId,
            byteSize = 4,
            checksum = "abcd",
          )

        completed.uploadStatus shouldBe AttachmentUploadStatus.COMPLETED
        attachments
          .listByWorkItem(stack.tenantId, issueId, purpose = null, commentApiId = null, 50, 0)
          .shouldHaveSize(1)

        shouldThrow<ResourceNotFoundException> {
          attachments.completePending(
            tenantId = stack.tenantId,
            issueId = issueId,
            attachmentApiId = "att_missing",
            uploadedBy = stack.actorId,
            byteSize = 4,
            checksum = "abcd",
          )
        }
      }
    }

    "listByWorkItem paginates in sql" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val projects = ExposedProjectRepository(database)
        val project = projects.findById(stack.tenantId, stack.projectId) ?: error("project missing")
        val workItems = ExposedWorkItemRepository(database)
        val attachments = ExposedWorkItemAttachmentRepository(database)
        val created =
          workItems.create(
            CreateWorkItemCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              issueTypeApiId = stack.issueType.apiId.value,
              title = "Pagination target",
              description = null,
              reporterId = stack.actorId,
              actorUserId = stack.actorId,
            ),
            issueTypeId = stack.issueType.id,
            issueTypeConfigId = stack.config.config.id,
            initialStatusId = stack.todoStatus.id,
            propertyValues = emptyList(),
          )
        val issueId = created.workItem.id
        val workItemApiId = created.workItem.apiId.value
        repeat(5) { index ->
          createCompletedAttachment(
            attachments = attachments,
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            projectApiId = project.apiId.value,
            workItemApiId = workItemApiId,
            issueId = issueId,
            uploadedBy = stack.actorId,
            filename = "file-$index.txt",
            contentType = "text/plain",
            byteSize = 8,
            purpose = AttachmentPurpose.STANDALONE,
            commentId = null,
            storageKey = "tenant/issue/pagination/file-$index.txt",
            checksum = "checksum-$index",
          )
        }

        attachments
          .listByWorkItem(
            stack.tenantId,
            issueId,
            purpose = null,
            commentApiId = null,
            limit = 2,
            offset = 0,
          )
          .shouldHaveSize(2)
        attachments
          .listByWorkItem(
            stack.tenantId,
            issueId,
            purpose = null,
            commentApiId = null,
            limit = 2,
            offset = 2,
          )
          .shouldHaveSize(2)
        attachments
          .listByWorkItem(
            stack.tenantId,
            issueId,
            purpose = null,
            commentApiId = null,
            limit = 2,
            offset = 4,
          )
          .shouldHaveSize(1)
        attachments
          .listByWorkItem(
            stack.tenantId,
            issueId,
            purpose = AttachmentPurpose.STANDALONE,
            commentApiId = null,
            limit = 10,
            offset = 0,
          )
          .shouldHaveSize(5)
      }
    }
  })

private suspend fun createCompletedAttachment(
  attachments: ExposedWorkItemAttachmentRepository,
  tenantId: UUID,
  projectId: UUID,
  projectApiId: String,
  workItemApiId: String,
  issueId: UUID,
  uploadedBy: UUID,
  filename: String,
  contentType: String,
  byteSize: Long,
  purpose: AttachmentPurpose,
  commentId: UUID?,
  storageKey: String,
  checksum: String,
  commentApiId: String? = null,
) =
  attachments
    .createPending(
      command =
        InitiateWorkItemAttachmentUploadCommand(
          tenantId = tenantId,
          projectId = projectId,
          projectApiId = projectApiId,
          workItemApiId = workItemApiId,
          uploadedBy = uploadedBy,
          filename = filename,
          contentType = contentType,
          declaredByteSize = byteSize,
          purpose = purpose,
          commentApiId = commentApiId,
        ),
      issueId = issueId,
      commentId = commentId,
      attachmentId = UUID.randomUUID(),
      apiId = PublicId.new("att"),
      storageKey = storageKey,
    )
    .let { pending ->
      attachments.completePending(
        tenantId = tenantId,
        issueId = issueId,
        attachmentApiId = pending.apiId.value,
        uploadedBy = uploadedBy,
        byteSize = byteSize,
        checksum = checksum,
      )
    }
