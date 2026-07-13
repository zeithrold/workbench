package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.agile.workitem.CreateWorkItemPersistenceCommand
import ink.doa.workbench.agile.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.agile.workitem.model.CreateWorkItemCommentCommand
import ink.doa.workbench.agile.workitem.model.DeleteWorkItemCommentCommand
import ink.doa.workbench.agile.workitem.model.UpdateWorkItemCommentCommand
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.withPostgresDatabase
import ink.doa.workbench.data.support.workItemCommentRepository
import ink.doa.workbench.data.support.workItemRepository
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class ExposedWorkItemCommentRepositoryIntegrationTest :
  StringSpec({
    "create list update and soft delete comments for a work item" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val workItems = workItemRepository(database)
        val comments = workItemCommentRepository(database)
        val created =
          workItems.create(
            CreateWorkItemPersistenceCommand(
              command =
                CreateWorkItemCommand(
                  tenantId = stack.tenantId,
                  projectId = stack.projectId,
                  issueTypeApiId = stack.issueType.apiId.value,
                  title = "Comment target",
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
                body = richText("First comment"),
                bodyPlainText = "First comment",
              ),
              issueId = issueId,
            )
            .record

        comment.body shouldBe richText("First comment")
        comments.findByApiId(stack.tenantId, issueId, comment.apiId.value).shouldNotBeNull()

        val updated =
          comments.update(
            UpdateWorkItemCommentCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              workItemApiId = workItemApiId,
              commentApiId = comment.apiId.value,
              actorUserId = stack.actorId,
              body = richText("Edited comment"),
              bodyPlainText = "Edited comment",
            ),
            issueId = issueId,
          )

        updated.body shouldBe richText("Edited comment")
        updated.editedAt.shouldNotBeNull()

        comments.softDelete(
          DeleteWorkItemCommentCommand(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            workItemApiId = workItemApiId,
            commentApiId = comment.apiId.value,
            actorUserId = stack.actorId,
            deleteReason = "cleanup",
          ),
          issueId = issueId,
        )

        comments.findByApiId(stack.tenantId, issueId, comment.apiId.value).shouldBeNull()
      }
    }

    "resolveIssueId returns api id for active work item" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val workItems = workItemRepository(database)
        val comments = workItemCommentRepository(database)
        val created =
          workItems.create(
            CreateWorkItemPersistenceCommand(
              command =
                CreateWorkItemCommand(
                  tenantId = stack.tenantId,
                  projectId = stack.projectId,
                  issueTypeApiId = stack.issueType.apiId.value,
                  title = "Resolvable",
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

        comments.resolveIssueId(
          stack.tenantId,
          stack.projectId,
          created.workItem.apiId.value,
        ) shouldBe created.workItem.id
        comments.resolveIssueId(stack.tenantId, stack.projectId, "iss_missing").shouldBeNull()
      }
    }

    "update throws when comment does not exist" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val comments = workItemCommentRepository(database)

        shouldThrow<ResourceNotFoundException> {
          comments.update(
            UpdateWorkItemCommentCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              workItemApiId = "iss_missing",
              commentApiId = "icm_missing",
              actorUserId = stack.actorId,
              body = richText("missing"),
            ),
            issueId = java.util.UUID.randomUUID(),
          )
        }
      }
    }
  })
