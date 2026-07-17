package one.ztd.workbench.data.repository.workitem

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import one.ztd.workbench.agile.workitem.CreateWorkItemPersistenceCommand
import one.ztd.workbench.agile.workitem.activity.WorkItemActivityPayload
import one.ztd.workbench.agile.workitem.model.CreateWorkItemCommand
import one.ztd.workbench.agile.workitem.model.CreateWorkItemCommentCommand
import one.ztd.workbench.agile.workitem.stream.WorkItemEventType
import one.ztd.workbench.agile.workitem.timeline.ListWorkItemTimelineQuery
import one.ztd.workbench.agile.workitem.timeline.WorkItemTimelineEntry
import one.ztd.workbench.data.support.seedWorkItemStack
import one.ztd.workbench.data.support.withPostgresDatabase
import one.ztd.workbench.data.support.workItemCommentRepository
import one.ztd.workbench.data.support.workItemRepository
import one.ztd.workbench.data.support.workItemTimelineRepository

class ExposedWorkItemTimelineRepositoryIntegrationTest :
  StringSpec({
    "lists merged timeline entries with cursor pagination and excludes comment event duplicates" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val workItems = workItemRepository(database)
        val comments = workItemCommentRepository(database)
        val timeline = workItemTimelineRepository(database)

        val created =
          workItems.create(
            CreateWorkItemPersistenceCommand(
              command =
                CreateWorkItemCommand(
                  tenantId = stack.tenantId,
                  projectId = stack.projectId,
                  issueTypeApiId = stack.issueType.apiId.value,
                  title = "Timeline target",
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
        val workItemApiId = created.workItem.apiId.value

        comments.create(
          CreateWorkItemCommentCommand(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            workItemApiId = workItemApiId,
            authorId = stack.actorId,
            body = richText("Ship it"),
            bodyPlainText = "Ship it",
          ),
          created.workItem.id,
        )

        val firstPage =
          timeline.listByWorkItem(
            ListWorkItemTimelineQuery(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              workItemApiId = workItemApiId,
              limit = 1,
            )
          )
        firstPage.items shouldHaveSize 1
        firstPage.items.single().shouldBeInstanceOf<WorkItemTimelineEntry.Comment>()
        firstPage.nextCursor.shouldNotBeNull()

        val secondPage =
          timeline.listByWorkItem(
            ListWorkItemTimelineQuery(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              workItemApiId = workItemApiId,
              limit = 10,
              cursor = firstPage.nextCursor,
            )
          )
        secondPage.items shouldHaveSize 1
        val eventEntry = secondPage.items.single()
        eventEntry.shouldBeInstanceOf<WorkItemTimelineEntry.Event>()
        eventEntry.record.eventType shouldBe WorkItemEventType.CREATED
        eventEntry.record.payload.shouldBeInstanceOf<WorkItemActivityPayload.Created>()
        secondPage.nextCursor.shouldBeNull()
      }
    }

    "soft deleting comment removes it from timeline" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val workItems = workItemRepository(database)
        val comments = workItemCommentRepository(database)
        val timeline = workItemTimelineRepository(database)

        val created =
          workItems.create(
            CreateWorkItemPersistenceCommand(
              command =
                CreateWorkItemCommand(
                  tenantId = stack.tenantId,
                  projectId = stack.projectId,
                  issueTypeApiId = stack.issueType.apiId.value,
                  title = "Delete comment target",
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
        val workItemApiId = created.workItem.apiId.value
        val comment =
          comments.create(
            CreateWorkItemCommentCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              workItemApiId = workItemApiId,
              authorId = stack.actorId,
              body = richText("Remove me"),
              bodyPlainText = "Remove me",
            ),
            created.workItem.id,
          )
        comments.softDelete(
          one.ztd.workbench.agile.workitem.model.DeleteWorkItemCommentCommand(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            workItemApiId = workItemApiId,
            commentApiId = comment.record.apiId.value,
            actorUserId = stack.actorId,
          ),
          created.workItem.id,
        )

        val page =
          timeline.listByWorkItem(
            ListWorkItemTimelineQuery(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              workItemApiId = workItemApiId,
              limit = 50,
            )
          )
        page.items.none { entry ->
          entry is WorkItemTimelineEntry.Comment && entry.comment.apiId == comment.record.apiId
        } shouldBe true
      }
    }
  })
