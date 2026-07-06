package ink.doa.workbench.core.workitem.timeline

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.common.pagination.WorkbenchCursor
import ink.doa.workbench.core.common.pagination.WorkbenchTimelineEntryKind
import ink.doa.workbench.core.workitem.activity.WorkItemActivityCommentRef
import ink.doa.workbench.core.workitem.activity.WorkItemActivityPayload
import ink.doa.workbench.core.workitem.activity.WorkItemActivityRecord
import ink.doa.workbench.core.workitem.activity.WorkItemActivitySourceType
import ink.doa.workbench.core.workitem.activity.WorkItemActivityType
import ink.doa.workbench.core.workitem.activity.WorkItemCommentCreatedPayload
import ink.doa.workbench.core.workitem.model.WorkItemCommentRecord
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class WorkItemTimelineRecordsCoverageTest :
  StringSpec({
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val workItemId = UUID.randomUUID()
    val authorId = UUID.randomUUID()

    "list timeline query carries cursor and limit" {
      val cursor =
        WorkbenchCursor(
          occurredAt = now,
          entryKind = WorkbenchTimelineEntryKind.ACTIVITY,
          entryId = UUID.randomUUID(),
        )

      ListWorkItemTimelineQuery(
          tenantId = tenantId,
          projectId = projectId,
          workItemApiId = "iss_test",
          limit = 25,
          cursor = cursor,
        )
        .cursor shouldBe cursor
    }

    "timeline page stores mixed activity and comment entries" {
      val activityRecord =
        WorkItemActivityRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("act"),
          tenantId = tenantId,
          projectId = projectId,
          workItemId = workItemId,
          workItemApiId = PublicId.new("iss"),
          actorUserId = authorId,
          actorApiId = PublicId.new("usr"),
          actorDisplayName = "Alice",
          activityType = WorkItemActivityType.COMMENT_CREATED,
          occurredAt = now,
          summary = "Commented",
          payload =
            WorkItemActivityPayload.CommentCreated(
              WorkItemCommentCreatedPayload(
                comment = WorkItemActivityCommentRef("cmt_test", "Looks good")
              )
            ),
          sourceType = WorkItemActivitySourceType.USER,
          createdAt = now,
        )
      val commentRecord =
        WorkItemCommentRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("cmt"),
          tenantId = tenantId,
          issueId = workItemId,
          authorId = authorId,
          authorApiId = PublicId.new("usr"),
          body = "<p>Looks good</p>",
          bodyPlainText = "Looks good",
          bodyFormat = "html",
          transitionId = null,
          statusHistoryId = null,
          activityId = activityRecord.id,
          editedAt = null,
          createdAt = now,
          updatedAt = now,
        )

      val page =
        WorkItemTimelinePage(
          items =
            listOf(
              WorkItemTimelineEntry.Activity(activityRecord),
              WorkItemTimelineEntry.Comment(commentRecord),
            ),
          nextCursor = null,
        )

      page.items shouldHaveSize 2
      page.nextCursor.shouldBeNull()
    }
  })
