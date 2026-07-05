package ink.doa.workbench.core.workitem.activity

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

class WorkItemActivityCodecTest :
  StringSpec({
    val codec = WorkItemActivityCodec()

    "round-trips created payload" {
      val payload =
        WorkItemCreatedPayload(
          status =
            WorkItemActivityStatusSnapshot(
              to = WorkItemActivityStatusRef("sts_test", "Todo", "todo")
            ),
          issueType = WorkItemActivityEntityRef("ity_test", "Task"),
        )
      val encoded = codec.encode(WorkItemActivitySpecs.Created, payload)
      val decoded = codec.decode(WorkItemActivityType.CREATED, encoded)
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
      val encoded = codec.encode(WorkItemActivitySpecs.Updated, payload)
      val decoded = codec.decode(WorkItemActivityType.UPDATED, encoded)
      decoded.shouldBeInstanceOf<WorkItemActivityPayload.Updated>()
    }

    "unknown activity type decodes to Unknown payload" {
      val raw = buildJsonObject {}
      val decoded = codec.decode(WorkItemActivityType.UNKNOWN, raw)
      decoded.shouldBeInstanceOf<WorkItemActivityPayload.Unknown>()
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
      val encoded = codec.encode(WorkItemActivitySpecs.StatusChanged, payload)
      val decoded = codec.decode(WorkItemActivityType.STATUS_CHANGED, encoded)
      decoded.shouldBeInstanceOf<WorkItemActivityPayload.StatusChanged>()
      decoded.value shouldBe payload
    }

    "round-trips comment created payload" {
      val payload =
        WorkItemCommentCreatedPayload(
          comment = WorkItemActivityCommentRef("cmt_test", "Looks good")
        )
      val encoded = codec.encode(WorkItemActivitySpecs.CommentCreated, payload)
      val decoded = codec.decode(WorkItemActivityType.COMMENT_CREATED, encoded)
      decoded.shouldBeInstanceOf<WorkItemActivityPayload.CommentCreated>()
      decoded.value shouldBe payload
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
        .decode(WorkItemActivityType.CREATED, codec.encodePayload(created))
        .shouldBeInstanceOf<WorkItemActivityPayload.Created>()

      val updated =
        WorkItemActivityPayload.Updated(
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
        )
      codec
        .decode(WorkItemActivityType.UPDATED, codec.encodePayload(updated))
        .shouldBeInstanceOf<WorkItemActivityPayload.Updated>()

      val statusChanged =
        WorkItemActivityPayload.StatusChanged(
          WorkItemStatusChangedPayload(
            status =
              WorkItemActivityStatusSnapshot(
                to = WorkItemActivityStatusRef("sts_test", "Done", "done")
              )
          )
        )
      codec
        .decode(WorkItemActivityType.STATUS_CHANGED, codec.encodePayload(statusChanged))
        .shouldBeInstanceOf<WorkItemActivityPayload.StatusChanged>()

      val commentCreated =
        WorkItemActivityPayload.CommentCreated(
          WorkItemCommentCreatedPayload(
            comment = WorkItemActivityCommentRef("cmt_test", "Looks good")
          )
        )
      codec
        .decode(WorkItemActivityType.COMMENT_CREATED, codec.encodePayload(commentCreated))
        .shouldBeInstanceOf<WorkItemActivityPayload.CommentCreated>()

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
      codec.validateRoundTrip(WorkItemActivitySpecs.Created, payload)
    }

    "invalid payload for known type throws" {
      val raw = JsonObject(emptyMap())
      shouldThrow<InvalidRequestException> {
          codec.decode(WorkItemActivityType.STATUS_CHANGED, raw)
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_ACTIVITY_PAYLOAD_INVALID
    }
  })
