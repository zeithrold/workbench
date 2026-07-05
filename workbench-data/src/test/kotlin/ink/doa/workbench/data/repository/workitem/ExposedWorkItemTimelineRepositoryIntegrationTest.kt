package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.core.workitem.CreateWorkItemPersistenceCommand
import ink.doa.workbench.core.workitem.activity.WorkItemActivityPayload
import ink.doa.workbench.core.workitem.activity.WorkItemActivityType
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommentCommand
import ink.doa.workbench.core.workitem.timeline.ListWorkItemTimelineQuery
import ink.doa.workbench.core.workitem.timeline.WorkItemTimelineEntry
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.withPostgresDatabase
import ink.doa.workbench.data.support.workItemCommentRepository
import ink.doa.workbench.data.support.workItemRepository
import ink.doa.workbench.data.support.workItemTimelineRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Tag

@Tag("integration")
class ExposedWorkItemTimelineRepositoryIntegrationTest :
  StringSpec({
    "lists merged timeline entries with cursor pagination and excludes comment activity duplicates" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val workItems = workItemRepository(database)
        val comments = workItemCommentRepository(database)
        val timeline = workItemTimelineRepository(database)
        val activities =
          ExposedWorkItemActivityRepository(
            database,
            ink.doa.workbench.core.workitem.activity.WorkItemActivityCodec(),
          )

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
        created.pendingActivity?.let { activities.createWithId(it) }
        val workItemApiId = created.workItem.apiId.value

        val commentResult =
          comments.create(
            CreateWorkItemCommentCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              workItemApiId = workItemApiId,
              authorId = stack.actorId,
              body = "<p>Ship it</p>",
              bodyPlainText = "Ship it",
            ),
            created.workItem.id,
          )
        commentResult.pendingActivity?.let { activities.createWithId(it) }

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
        val activityEntry = secondPage.items.single()
        activityEntry.shouldBeInstanceOf<WorkItemTimelineEntry.Activity>()
        activityEntry.record.activityType shouldBe WorkItemActivityType.CREATED
        activityEntry.record.payload.shouldBeInstanceOf<WorkItemActivityPayload.Created>()
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
              body = "<p>Remove me</p>",
              bodyPlainText = "Remove me",
            ),
            created.workItem.id,
          )
        comments.softDelete(
          ink.doa.workbench.core.workitem.model.DeleteWorkItemCommentCommand(
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
          entry is WorkItemTimelineEntry.Comment && entry.record.apiId == comment.record.apiId
        } shouldBe true
      }
    }
  })
