package ink.doa.workbench.core.workitem.activity

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.messaging.DomainTopics
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
      WorkItemActivitySpecs.specFor(WorkItemActivityType.UNKNOWN).shouldBeNull()
    }

    "record requested domain event spec uses work item activity topic" {
      WorkItemActivityDomainEvents.RecordRequested.type shouldBe
        "work_item.activity.record_requested"
      WorkItemActivityDomainEvents.RecordRequested.topic shouldBe DomainTopics.WORK_ITEM_ACTIVITY
    }

    "activity records store actor and payload metadata" {
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
        WorkItemActivityRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("act"),
          tenantId = tenantId,
          projectId = projectId,
          workItemId = workItemId,
          workItemApiId = PublicId.new("iss"),
          actorUserId = userId,
          actorApiId = PublicId.new("usr"),
          actorDisplayName = "Alice",
          activityType = WorkItemActivityType.CREATED,
          occurredAt = now,
          summary = "Created",
          payload = payload,
          sourceType = WorkItemActivitySourceType.USER,
          createdAt = now,
        )
      record.summary shouldBe "Created"
    }

    "create activity command stores actor and source metadata" {
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
      CreateWorkItemActivityCommand(
          tenantId = tenantId,
          projectId = projectId,
          workItemId = workItemId,
          actorUserId = userId,
          spec = WorkItemActivitySpecs.Updated,
          payload = payload,
          occurredAt = now,
          summary = "Updated title",
          sourceType = WorkItemActivitySourceType.AUTOMATION,
          sourceId = "rule_1",
          correlationId = "corr-1",
          requestId = "req-1",
        )
        .summary shouldBe "Updated title"

      PendingWorkItemActivity(
          id = UUID.randomUUID(),
          apiId = PublicId.new("act"),
          command =
            CreateWorkItemActivityCommand(
              tenantId = tenantId,
              projectId = projectId,
              workItemId = workItemId,
              actorUserId = userId,
              spec = WorkItemActivitySpecs.Updated,
              payload = payload,
              occurredAt = now,
            ),
        )
        .command
        .spec
        .type shouldBe WorkItemActivityType.UPDATED
    }

    "unknown payload wrapper keeps raw json" {
      val raw = buildJsonObject {}
      WorkItemActivityPayload.Unknown(raw).raw shouldBe raw
    }
  })
