package ink.doa.workbench.agile.workitem.stream

import ink.doa.workbench.agile.workitem.activity.WorkItemActivityCommentRef
import ink.doa.workbench.agile.workitem.activity.WorkItemActivityEntityRef
import ink.doa.workbench.agile.workitem.activity.WorkItemActivityFieldChange
import ink.doa.workbench.agile.workitem.activity.WorkItemActivityPayload
import ink.doa.workbench.agile.workitem.activity.WorkItemActivityStatusRef
import ink.doa.workbench.agile.workitem.activity.WorkItemActivityStatusSnapshot
import ink.doa.workbench.agile.workitem.activity.WorkItemCommentCreatedPayload
import ink.doa.workbench.agile.workitem.activity.WorkItemCommentDeletedPayload
import ink.doa.workbench.agile.workitem.activity.WorkItemCommentEditedPayload
import ink.doa.workbench.agile.workitem.activity.WorkItemCreatedPayload
import ink.doa.workbench.agile.workitem.activity.WorkItemStatusChangedPayload
import ink.doa.workbench.agile.workitem.activity.WorkItemUpdatedPayload
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

class WorkItemEventCodecTest :
  StringSpec({
    val codec = WorkItemEventCodec()

    "round-trips created payload" {
      val payload =
        WorkItemCreatedPayload(
          status =
            WorkItemActivityStatusSnapshot(
              to = WorkItemActivityStatusRef("sts_test", "Todo", "todo")
            ),
          issueType = WorkItemActivityEntityRef("ity_test", "Task"),
        )
      val encoded = codec.encode(WorkItemEventSpecs.Created, payload)
      val decoded = codec.decode(WorkItemEventType.CREATED, encoded)
      decoded.shouldBeInstanceOf<WorkItemActivityPayload.Created>()
      decoded.value shouldBe payload
    }

    "round-trips updated payload" {
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
      val encoded = codec.encode(WorkItemEventSpecs.Updated, payload)
      val decoded = codec.decode(WorkItemEventType.UPDATED, encoded)
      decoded.shouldBeInstanceOf<WorkItemActivityPayload.Updated>()
    }

    "round-trips status changed payload" {
      val payload =
        WorkItemStatusChangedPayload(
          status =
            WorkItemActivityStatusSnapshot(
              from = WorkItemActivityStatusRef("sts_old", "Todo", "todo"),
              to = WorkItemActivityStatusRef("sts_new", "Done", "done"),
            )
        )
      val encoded = codec.encode(WorkItemEventSpecs.StatusChanged, payload)
      val decoded = codec.decode(WorkItemEventType.STATUS_CHANGED, encoded)
      decoded.shouldBeInstanceOf<WorkItemActivityPayload.StatusChanged>()
      decoded.value shouldBe payload
    }

    "round-trips comment added payload" {
      val payload =
        WorkItemCommentCreatedPayload(
          comment = WorkItemActivityCommentRef("cmt_test", "Looks good")
        )
      val encoded = codec.encode(WorkItemEventSpecs.CommentAdded, payload)
      val decoded = codec.decode(WorkItemEventType.COMMENT_ADDED, encoded)
      decoded.shouldBeInstanceOf<WorkItemActivityPayload.CommentAdded>()
      decoded.value shouldBe payload
    }

    "round-trips comment edited payload" {
      val payload =
        WorkItemCommentEditedPayload(
          comment = WorkItemActivityCommentRef("cmt_test", "Updated text")
        )
      val encoded = codec.encode(WorkItemEventSpecs.CommentEdited, payload)
      val decoded = codec.decode(WorkItemEventType.COMMENT_EDITED, encoded)
      decoded.shouldBeInstanceOf<WorkItemActivityPayload.CommentEdited>()
      decoded.value shouldBe payload
    }

    "round-trips comment deleted payload" {
      val payload =
        WorkItemCommentDeletedPayload(
          comment = WorkItemActivityCommentRef("cmt_test", "Removed"),
          deleteReason = "spam",
        )
      val encoded = codec.encode(WorkItemEventSpecs.CommentDeleted, payload)
      val decoded = codec.decode(WorkItemEventType.COMMENT_DELETED, encoded)
      decoded.shouldBeInstanceOf<WorkItemActivityPayload.CommentDeleted>()
      decoded.value shouldBe payload
    }

    "unknown event type decodes to Unknown payload" {
      val raw = buildJsonObject {}
      val decoded = codec.decode(WorkItemEventType.UNKNOWN, raw)
      decoded.shouldBeInstanceOf<WorkItemActivityPayload.Unknown>()
    }

    "encodePayload round-trips all payload variants" {
      val created =
        WorkItemActivityPayload.Created(
          WorkItemCreatedPayload(
            status =
              WorkItemActivityStatusSnapshot(
                to = WorkItemActivityStatusRef("sts_test", "Todo", "todo")
              ),
            issueType = WorkItemActivityEntityRef("ity_test", "Task"),
          )
        )
      codec
        .decode(WorkItemEventType.CREATED, codec.encodePayload(created))
        .shouldBeInstanceOf<WorkItemActivityPayload.Created>()

      val commentAdded =
        WorkItemActivityPayload.CommentAdded(
          WorkItemCommentCreatedPayload(
            comment = WorkItemActivityCommentRef("cmt_test", "Looks good")
          )
        )
      codec
        .decode(WorkItemEventType.COMMENT_ADDED, codec.encodePayload(commentAdded))
        .shouldBeInstanceOf<WorkItemActivityPayload.CommentAdded>()

      val raw = buildJsonObject {}
      codec.encodePayload(WorkItemActivityPayload.Unknown(raw)) shouldBe raw
    }

    "validateRoundTrip accepts valid payload" {
      val payload =
        WorkItemCreatedPayload(
          status =
            WorkItemActivityStatusSnapshot(
              to = WorkItemActivityStatusRef("sts_test", "Todo", "todo")
            ),
          issueType = WorkItemActivityEntityRef("ity_test", "Task"),
        )
      codec.validateRoundTrip(WorkItemEventSpecs.Created, payload)
    }

    "invalid payload for known type throws" {
      val raw = JsonObject(emptyMap())
      shouldThrow<InvalidRequestException> { codec.decode(WorkItemEventType.STATUS_CHANGED, raw) }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_ACTIVITY_PAYLOAD_INVALID
    }
  })
