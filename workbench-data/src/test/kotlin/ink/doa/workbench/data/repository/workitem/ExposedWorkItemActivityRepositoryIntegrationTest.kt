package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.core.workitem.CreateWorkItemPersistenceCommand
import ink.doa.workbench.core.workitem.activity.ListWorkItemActivitiesQuery
import ink.doa.workbench.core.workitem.activity.WorkItemActivityCodec
import ink.doa.workbench.core.workitem.activity.WorkItemActivityPayload
import ink.doa.workbench.core.workitem.activity.WorkItemActivityType
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommentCommand
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.withPostgresDatabase
import ink.doa.workbench.data.support.workItemCommentRepository
import ink.doa.workbench.data.support.workItemRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Tag

@Tag("integration")
class ExposedWorkItemActivityRepositoryIntegrationTest :
  StringSpec({
    "create list and paginate work item activities" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val codec = WorkItemActivityCodec()
        val activities = ExposedWorkItemActivityRepository(database, codec)
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
                  title = "Activity target",
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

        val page =
          activities.listByWorkItem(
            ListWorkItemActivitiesQuery(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              workItemApiId = workItemApiId,
              limit = 50,
            )
          )
        page.items shouldHaveSize 2
        page.items[0].activityType shouldBe WorkItemActivityType.COMMENT_CREATED
        page.items[0].payload.shouldBeInstanceOf<WorkItemActivityPayload.CommentCreated>()
        page.items[1].activityType shouldBe WorkItemActivityType.CREATED
        page.items[1].payload.shouldBeInstanceOf<WorkItemActivityPayload.Created>()
        page.page.nextBefore shouldBe null
      }
    }
  })
