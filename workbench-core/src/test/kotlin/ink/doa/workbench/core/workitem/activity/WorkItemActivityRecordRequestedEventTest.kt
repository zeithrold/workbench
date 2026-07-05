package ink.doa.workbench.core.workitem.activity

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.ids.PublicId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject

class WorkItemActivityRecordRequestedEventTest :
  StringSpec({
    val codec = WorkItemActivityCodec()
    val tenantId = UUID.fromString("00000000-0000-0000-0000-000000000010")
    val projectId = UUID.fromString("00000000-0000-0000-0000-000000000011")
    val workItemId = UUID.fromString("00000000-0000-0000-0000-000000000012")
    val actorUserId = UUID.fromString("00000000-0000-0000-0000-000000000013")
    val activityId = UUID.fromString("00000000-0000-0000-0000-000000000014")
    val occurredAt = OffsetDateTime.parse("2026-07-03T09:15:30Z")

    fun createdPayload() =
      WorkItemCreatedPayload(
        status =
          WorkItemActivityStatusSnapshot(
            to = WorkItemActivityStatusRef("sts_test", "Todo", "todo")
          ),
        issueType = WorkItemActivityEntityRef("ity_test", "Task"),
      )

    fun pendingCreatedCommand() =
      CreateWorkItemActivityCommand(
        tenantId = tenantId,
        projectId = projectId,
        workItemId = workItemId,
        actorUserId = actorUserId,
        spec = WorkItemActivitySpecs.Created,
        payload = createdPayload(),
        occurredAt = occurredAt,
        summary = "Created work item",
        sourceType = WorkItemActivitySourceType.USER,
        sourceId = "usr_actor",
        correlationId = "corr-1",
        requestId = "req-1",
      )

    fun pendingCreated() =
      PendingWorkItemActivity(
        id = activityId,
        apiId = PublicId.new("act"),
        command = pendingCreatedCommand(),
      )

    "maps pending activity to record requested event" {
      val pending = pendingCreated()
      val event = WorkItemActivityRecordRequestedEvent.from(pending, codec)

      event.activityId shouldBe activityId.toString()
      event.activityApiId shouldBe pending.apiId.value
      event.tenantId shouldBe tenantId.toString()
      event.projectId shouldBe projectId.toString()
      event.workItemId shouldBe workItemId.toString()
      event.actorUserId shouldBe actorUserId.toString()
      event.activityType shouldBe WorkItemActivityType.CREATED.dbValue
      event.occurredAt shouldBe occurredAt.toString()
      event.summary shouldBe "Created work item"
      event.sourceType shouldBe WorkItemActivitySourceType.USER.dbValue
      event.sourceId shouldBe "usr_actor"
      event.correlationId shouldBe "corr-1"
      event.requestId shouldBe "req-1"
    }

    "round-trips pending activity through event" {
      val pending = pendingCreated()
      val event = WorkItemActivityRecordRequestedEvent.from(pending, codec)
      val restored = WorkItemActivityRecordRequestedEvent.toPending(event, codec)

      restored.id shouldBe pending.id
      restored.apiId shouldBe pending.apiId
      restored.command.tenantId shouldBe tenantId
      restored.command.projectId shouldBe projectId
      restored.command.workItemId shouldBe workItemId
      restored.command.actorUserId shouldBe actorUserId
      restored.command.spec.type shouldBe WorkItemActivityType.CREATED
      restored.command.payload shouldBe createdPayload()
      restored.command.occurredAt shouldBe occurredAt
      restored.command.summary shouldBe "Created work item"
      restored.command.sourceType shouldBe WorkItemActivitySourceType.USER
      restored.command.sourceId shouldBe "usr_actor"
      restored.command.correlationId shouldBe "corr-1"
      restored.command.requestId shouldBe "req-1"
    }

    "toCommand decodes updated activity payload" {
      val command =
        CreateWorkItemActivityCommand(
          tenantId = tenantId,
          projectId = projectId,
          workItemId = workItemId,
          actorUserId = null,
          spec = WorkItemActivitySpecs.Updated,
          payload =
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
            ),
          occurredAt = occurredAt,
          sourceType = WorkItemActivitySourceType.SYSTEM,
        )
      val pending =
        PendingWorkItemActivity(
          id = activityId,
          apiId = PublicId.new("act"),
          command = command,
        )
      val event = WorkItemActivityRecordRequestedEvent.from(pending, codec)
      val restored = WorkItemActivityRecordRequestedEvent.toCommand(event, codec)

      restored.spec.type shouldBe WorkItemActivityType.UPDATED
      restored.actorUserId shouldBe null
      restored.sourceType shouldBe WorkItemActivitySourceType.SYSTEM
      restored.payload.shouldBeInstanceOf<WorkItemUpdatedPayload>()
    }

    "toCommand decodes status changed activity payload" {
      val payload =
        WorkItemStatusChangedPayload(
          status =
            WorkItemActivityStatusSnapshot(
              from = WorkItemActivityStatusRef("sts_old", "Todo", "todo"),
              to = WorkItemActivityStatusRef("sts_new", "Done", "done"),
              transition = WorkItemActivityEntityRef("trn_test", "Resolve"),
            )
        )
      val pending =
        PendingWorkItemActivity(
          id = activityId,
          apiId = PublicId.new("act"),
          command =
            CreateWorkItemActivityCommand(
              tenantId = tenantId,
              projectId = projectId,
              workItemId = workItemId,
              actorUserId = actorUserId,
              spec = WorkItemActivitySpecs.StatusChanged,
              payload = payload,
              occurredAt = occurredAt,
            ),
        )
      val event = WorkItemActivityRecordRequestedEvent.from(pending, codec)
      val restored = WorkItemActivityRecordRequestedEvent.toCommand(event, codec)

      restored.spec.type shouldBe WorkItemActivityType.STATUS_CHANGED
      restored.payload shouldBe payload
    }

    "toCommand decodes comment created activity payload" {
      val payload =
        WorkItemCommentCreatedPayload(
          comment = WorkItemActivityCommentRef("cmt_test", "Looks good")
        )
      val pending =
        PendingWorkItemActivity(
          id = activityId,
          apiId = PublicId.new("act"),
          command =
            CreateWorkItemActivityCommand(
              tenantId = tenantId,
              projectId = projectId,
              workItemId = workItemId,
              actorUserId = actorUserId,
              spec = WorkItemActivitySpecs.CommentCreated,
              payload = payload,
              occurredAt = occurredAt,
            ),
        )
      val event = WorkItemActivityRecordRequestedEvent.from(pending, codec)
      val restored = WorkItemActivityRecordRequestedEvent.toCommand(event, codec)

      restored.spec.type shouldBe WorkItemActivityType.COMMENT_CREATED
      restored.payload shouldBe payload
    }

    "toCommand rejects unknown activity type" {
      val pending = pendingCreated()
      val event =
        WorkItemActivityRecordRequestedEvent.from(pending, codec)
          .copy(activityType = "work_item.unknown")

      shouldThrow<IllegalArgumentException> {
        WorkItemActivityRecordRequestedEvent.toCommand(event, codec)
      }
    }

    "toCommand rejects invalid payload for known activity type" {
      val pending = pendingCreated()
      val event =
        WorkItemActivityRecordRequestedEvent.from(pending, codec).copy(payload = buildJsonObject {})

      shouldThrow<InvalidRequestException> {
        WorkItemActivityRecordRequestedEvent.toCommand(event, codec)
      }
    }
  })
