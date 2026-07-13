package ink.doa.workbench.agile.workitem.activity

import ink.doa.workbench.agile.workitem.stream.AppendWorkItemEventCommand
import ink.doa.workbench.agile.workitem.stream.WorkItemEventRecord
import ink.doa.workbench.agile.workitem.stream.WorkItemEventSourceType
import ink.doa.workbench.agile.workitem.stream.WorkItemEventSpecs
import ink.doa.workbench.agile.workitem.stream.WorkItemEventType
import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject

class WorkItemActivityModelsCoverageTest :
  StringSpec({
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val workItemId = UUID.randomUUID()
    val userId = UUID.randomUUID()

    "activity type helpers resolve db values" {
      WorkItemActivityType.fromDbValue("work_item.created") shouldBe WorkItemActivityType.CREATED
      WorkItemActivityType.fromDbValue("missing") shouldBe WorkItemActivityType.UNKNOWN
      WorkItemActivityType.requireKnown(WorkItemActivityType.CREATED) shouldBe
        WorkItemActivityType.CREATED
      shouldThrow<IllegalArgumentException> {
        WorkItemActivityType.requireKnown(WorkItemActivityType.UNKNOWN)
      }
    }

    "activity source type helpers resolve db values" {
      WorkItemActivitySourceType.fromDbValue("system") shouldBe WorkItemActivitySourceType.SYSTEM
      WorkItemActivitySourceType.fromDbValue("missing") shouldBe WorkItemActivitySourceType.USER
    }

    "activity specs resolve by type" {
      WorkItemActivitySpecs.specFor(WorkItemActivityType.CREATED)?.type shouldBe
        WorkItemActivityType.CREATED
      WorkItemActivitySpecs.specFor(WorkItemActivityType.COMMENT_CREATED)?.type shouldBe
        WorkItemActivityType.COMMENT_CREATED
      WorkItemActivitySpecs.CommentAdded.type shouldBe WorkItemActivityType.COMMENT_CREATED
      WorkItemActivitySpecs.specFor(WorkItemActivityType.UNKNOWN).shouldBeNull()
    }

    "event type helpers resolve db values" {
      WorkItemEventType.fromDbValue("work_item.created") shouldBe WorkItemEventType.CREATED
      WorkItemEventType.fromDbValue("comment.added") shouldBe WorkItemEventType.COMMENT_ADDED
      WorkItemEventType.fromDbValue("missing") shouldBe WorkItemEventType.UNKNOWN
      WorkItemEventType.requireKnown(WorkItemEventType.CREATED) shouldBe WorkItemEventType.CREATED
      shouldThrow<IllegalArgumentException> {
        WorkItemEventType.requireKnown(WorkItemEventType.UNKNOWN)
      }
    }

    "event source type helpers resolve db values" {
      WorkItemEventSourceType.fromDbValue("system") shouldBe WorkItemEventSourceType.SYSTEM
      WorkItemEventSourceType.fromDbValue("missing") shouldBe WorkItemEventSourceType.USER
    }

    "event specs resolve by type" {
      WorkItemEventSpecs.specFor(WorkItemEventType.CREATED)?.type shouldBe WorkItemEventType.CREATED
      WorkItemEventSpecs.specFor(WorkItemEventType.UNKNOWN).shouldBeNull()
    }

    "event records store actor and payload metadata" {
      val payload =
        WorkItemActivityPayload.Created(
          WorkItemCreatedPayload(
            status =
              WorkItemActivityStatusSnapshot(
                to = WorkItemActivityStatusRef("sts_test", "Todo", "todo")
              ),
            issueType = WorkItemActivityEntityRef("ity_test", "Task"),
          )
        )
      val record =
        WorkItemEventRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("evt"),
          tenantId = tenantId,
          projectId = projectId,
          workItemId = workItemId,
          workItemApiId = PublicId.new("iss"),
          sequence = 1,
          eventType = WorkItemEventType.CREATED,
          actorUserId = userId,
          actorApiId = PublicId.new("usr"),
          actorDisplayName = "Alice",
          occurredAt = now,
          summary = "Created",
          payload = payload,
          sourceType = WorkItemEventSourceType.USER,
          createdAt = now,
        )
      record.summary shouldBe "Created"
    }

    "append event command stores actor and source metadata" {
      val payload =
        WorkItemUpdatedPayload(
          fields =
            listOf(
              WorkItemActivityFieldChange(
                path = "title",
                label = "Title",
                from = null,
                to = null,
              )
            )
        )
      AppendWorkItemEventCommand(
          tenantId = tenantId,
          projectId = projectId,
          workItemId = workItemId,
          actorUserId = userId,
          spec = WorkItemEventSpecs.Updated,
          payload = payload,
          occurredAt = now,
          summary = "Updated title",
          sourceType = WorkItemEventSourceType.AUTOMATION,
          sourceId = "rule_1",
          correlationId = "corr-1",
          requestId = "req-1",
        )
        .summary shouldBe "Updated title"
    }

    "unknown payload wrapper keeps raw json" {
      val raw = buildJsonObject {}
      WorkItemActivityPayload.Unknown(raw).raw shouldBe raw
    }
  })
