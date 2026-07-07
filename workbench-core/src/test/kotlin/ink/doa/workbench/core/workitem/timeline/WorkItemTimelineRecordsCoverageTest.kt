package ink.doa.workbench.core.workitem.timeline

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.common.pagination.WorkItemStreamCursor
import ink.doa.workbench.core.workitem.activity.WorkItemActivityCommentRef
import ink.doa.workbench.core.workitem.activity.WorkItemActivityPayload
import ink.doa.workbench.core.workitem.activity.WorkItemCommentCreatedPayload
import ink.doa.workbench.core.workitem.model.WorkItemCommentRecord
import ink.doa.workbench.core.workitem.stream.WorkItemEventRecord
import ink.doa.workbench.core.workitem.stream.WorkItemEventSourceType
import ink.doa.workbench.core.workitem.stream.WorkItemEventType
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
      val cursor = WorkItemStreamCursor(sequence = 42)

      ListWorkItemTimelineQuery(
          tenantId = tenantId,
          projectId = projectId,
          workItemApiId = "iss_test",
          limit = 25,
          cursor = cursor,
        )
        .cursor shouldBe cursor
    }

    "timeline page stores mixed event and comment entries" {
      val eventRecord =
        WorkItemEventRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("evt"),
          tenantId = tenantId,
          projectId = projectId,
          workItemId = workItemId,
          workItemApiId = PublicId.new("iss"),
          sequence = 2,
          eventType = WorkItemEventType.COMMENT_ADDED,
          actorUserId = authorId,
          actorApiId = PublicId.new("usr"),
          actorDisplayName = "Alice",
          occurredAt = now,
          summary = "Commented",
          payload =
            WorkItemActivityPayload.CommentAdded(
              WorkItemCommentCreatedPayload(
                comment = WorkItemActivityCommentRef("cmt_test", "Looks good")
              )
            ),
          sourceType = WorkItemEventSourceType.USER,
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
          editedAt = null,
          createdAt = now,
          updatedAt = now,
        )

      val page =
        WorkItemTimelinePage(
          items =
            listOf(
              WorkItemTimelineEntry.Event(eventRecord),
              WorkItemTimelineEntry.Comment(event = eventRecord, comment = commentRecord),
            ),
          nextCursor = null,
        )

      page.items shouldHaveSize 2
      page.nextCursor.shouldBeNull()
    }
  })
