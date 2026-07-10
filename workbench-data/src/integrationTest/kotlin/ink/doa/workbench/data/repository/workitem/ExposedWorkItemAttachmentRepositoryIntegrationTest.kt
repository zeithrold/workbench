package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.CreateWorkItemPersistenceCommand
import ink.doa.workbench.core.workitem.model.AttachmentPurpose
import ink.doa.workbench.core.workitem.model.AttachmentUploadStatus
import ink.doa.workbench.core.workitem.model.CompletePendingAttachmentCommand
import ink.doa.workbench.core.workitem.model.CreatePendingAttachmentCommand
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommentCommand
import ink.doa.workbench.core.workitem.model.DeleteWorkItemAttachmentCommand
import ink.doa.workbench.core.workitem.model.InitiateWorkItemAttachmentUploadCommand
import ink.doa.workbench.core.workitem.model.ListWorkItemAttachmentsQuery
import ink.doa.workbench.data.repository.project.ExposedProjectRepository
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.withPostgresDatabase
import ink.doa.workbench.data.support.workItemCommentRepository
import ink.doa.workbench.data.support.workItemRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

class ExposedWorkItemAttachmentRepositoryIntegrationTest :
  StringSpec({
    "create list filter and soft delete attachments" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val projects = ExposedProjectRepository(database)
        val project = projects.findById(stack.tenantId, stack.projectId) ?: error("project missing")
        val workItems = workItemRepository(database)
        val comments = workItemCommentRepository(database)
        val attachments = ExposedWorkItemAttachmentRepository(database)
        val created =
          workItems.create(
            CreateWorkItemPersistenceCommand(
              command =
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
          )
        val issueId = created.workItem.id
        val workItemApiId = created.workItem.apiId.value
        val comment =
          comments
            .create(
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
            .record

        val standalone =
          createCompletedAttachment(
            CreateCompletedAttachmentParams(
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
          )
        val descriptionAttachment =
          createCompletedAttachment(
            CreateCompletedAttachmentParams(
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
          )
        val commentAttachment =
          createCompletedAttachment(
            CreateCompletedAttachmentParams(
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
          )

        attachments
          .listByWorkItem(
            ListWorkItemAttachmentsQuery(
              tenantId = stack.tenantId,
              issueId = issueId,
              limit = 50,
              offset = 0,
            )
          )
          .shouldHaveSize(3)
        attachments
          .listByWorkItem(
            ListWorkItemAttachmentsQuery(
              tenantId = stack.tenantId,
              issueId = issueId,
              purpose = AttachmentPurpose.STANDALONE,
              limit = 50,
              offset = 0,
            )
          )
          .shouldHaveSize(1)
          .first()
          .apiId shouldBe standalone.apiId
        attachments
          .listByWorkItem(
            ListWorkItemAttachmentsQuery(
              tenantId = stack.tenantId,
              issueId = issueId,
              purpose = AttachmentPurpose.COMMENT,
              commentApiId = comment.apiId.value,
              limit = 50,
              offset = 0,
            )
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
        val workItems = workItemRepository(database)
        val attachments = ExposedWorkItemAttachmentRepository(database)
        val created =
          workItems.create(
            CreateWorkItemPersistenceCommand(
              command =
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
          )
        val issueId = created.workItem.id
        val pending =
          attachments.createPending(
            CreatePendingAttachmentCommand(
              upload =
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
          )

        pending.uploadStatus shouldBe AttachmentUploadStatus.PENDING
        attachments
          .listByWorkItem(
            ListWorkItemAttachmentsQuery(
              tenantId = stack.tenantId,
              issueId = issueId,
              limit = 50,
              offset = 0,
            )
          )
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
            CompletePendingAttachmentCommand(
              tenantId = stack.tenantId,
              issueId = issueId,
              attachmentApiId = pending.apiId.value,
              uploadedBy = stack.actorId,
              byteSize = 4,
              checksum = "abcd",
            )
          )

        completed.uploadStatus shouldBe AttachmentUploadStatus.COMPLETED
        attachments
          .listByWorkItem(
            ListWorkItemAttachmentsQuery(
              tenantId = stack.tenantId,
              issueId = issueId,
              limit = 50,
              offset = 0,
            )
          )
          .shouldHaveSize(1)

        shouldThrow<ResourceNotFoundException> {
          attachments.completePending(
            CompletePendingAttachmentCommand(
              tenantId = stack.tenantId,
              issueId = issueId,
              attachmentApiId = "att_missing",
              uploadedBy = stack.actorId,
              byteSize = 4,
              checksum = "abcd",
            )
          )
        }
      }
    }

    "listByWorkItem paginates in sql" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val projects = ExposedProjectRepository(database)
        val project = projects.findById(stack.tenantId, stack.projectId) ?: error("project missing")
        val workItems = workItemRepository(database)
        val attachments = ExposedWorkItemAttachmentRepository(database)
        val created =
          workItems.create(
            CreateWorkItemPersistenceCommand(
              command =
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
          )
        val issueId = created.workItem.id
        val workItemApiId = created.workItem.apiId.value
        repeat(5) { index ->
          createCompletedAttachment(
            CreateCompletedAttachmentParams(
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
          )
        }

        attachments
          .listByWorkItem(
            ListWorkItemAttachmentsQuery(
              tenantId = stack.tenantId,
              issueId = issueId,
              limit = 2,
              offset = 0,
            )
          )
          .shouldHaveSize(2)
        attachments
          .listByWorkItem(
            ListWorkItemAttachmentsQuery(
              tenantId = stack.tenantId,
              issueId = issueId,
              limit = 2,
              offset = 2,
            )
          )
          .shouldHaveSize(2)
        attachments
          .listByWorkItem(
            ListWorkItemAttachmentsQuery(
              tenantId = stack.tenantId,
              issueId = issueId,
              limit = 2,
              offset = 4,
            )
          )
          .shouldHaveSize(1)
        attachments
          .listByWorkItem(
            ListWorkItemAttachmentsQuery(
              tenantId = stack.tenantId,
              issueId = issueId,
              purpose = AttachmentPurpose.STANDALONE,
              limit = 10,
              offset = 0,
            )
          )
          .shouldHaveSize(5)
      }
    }
  })

private data class CreateCompletedAttachmentParams(
  val attachments: ExposedWorkItemAttachmentRepository,
  val tenantId: UUID,
  val projectId: UUID,
  val projectApiId: String,
  val workItemApiId: String,
  val issueId: UUID,
  val uploadedBy: UUID,
  val filename: String,
  val contentType: String,
  val byteSize: Long,
  val purpose: AttachmentPurpose,
  val commentId: UUID?,
  val storageKey: String,
  val checksum: String,
  val commentApiId: String? = null,
)

private suspend fun createCompletedAttachment(params: CreateCompletedAttachmentParams) =
  params.attachments
    .createPending(
      CreatePendingAttachmentCommand(
        upload =
          InitiateWorkItemAttachmentUploadCommand(
            tenantId = params.tenantId,
            projectId = params.projectId,
            projectApiId = params.projectApiId,
            workItemApiId = params.workItemApiId,
            uploadedBy = params.uploadedBy,
            filename = params.filename,
            contentType = params.contentType,
            declaredByteSize = params.byteSize,
            purpose = params.purpose,
            commentApiId = params.commentApiId,
          ),
        issueId = params.issueId,
        commentId = params.commentId,
        attachmentId = UUID.randomUUID(),
        apiId = PublicId.new("att"),
        storageKey = params.storageKey,
      )
    )
    .let { pending ->
      params.attachments.completePending(
        CompletePendingAttachmentCommand(
          tenantId = params.tenantId,
          issueId = params.issueId,
          attachmentApiId = pending.apiId.value,
          uploadedBy = params.uploadedBy,
          byteSize = params.byteSize,
          checksum = params.checksum,
        )
      )
    }
