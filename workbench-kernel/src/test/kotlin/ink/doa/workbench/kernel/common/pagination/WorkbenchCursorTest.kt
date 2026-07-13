package ink.doa.workbench.kernel.common.pagination

import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.json.Json

class WorkbenchCursorTest :
  StringSpec({
    val occurredAt = OffsetDateTime.parse("2026-07-03T12:00:00Z")
    val entryId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    "encodes and decodes cursor payload" {
      val cursor =
        WorkbenchCursor(
          occurredAt = occurredAt,
          entryKind = WorkbenchTimelineEntryKind.ACTIVITY,
          entryId = entryId,
        )
      val encoded = cursor.encode()
      val decoded = WorkbenchCursor.decode(encoded)
      decoded.occurredAt shouldBe occurredAt
      decoded.entryKind shouldBe WorkbenchTimelineEntryKind.ACTIVITY
      decoded.entryId shouldBe entryId
    }

    "rejects invalid cursor token" {
      shouldThrow<InvalidRequestException> { WorkbenchCursor.decode("not-a-cursor") }
        .errorCode shouldBe WorkbenchErrorCode.REQUEST_INVALID
    }

    "rejects cursor with unknown entry kind" {
      val payload =
        WorkbenchCursorPayload(
          at = "2026-07-03T12:00:00Z",
          kind = "unknown",
          id = "11111111-1111-1111-1111-111111111111",
        )
      val encoded =
        Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(Json.encodeToString(payload).encodeToByteArray())

      shouldThrow<InvalidRequestException> { WorkbenchCursor.decode(encoded) }.errorCode shouldBe
        WorkbenchErrorCode.REQUEST_INVALID
    }

    "rejects cursor with invalid timestamp" {
      val payload =
        WorkbenchCursorPayload(
          at = "not-a-date",
          kind = "activity",
          id = "11111111-1111-1111-1111-111111111111",
        )
      val encoded =
        Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(Json.encodeToString(payload).encodeToByteArray())

      shouldThrow<InvalidRequestException> { WorkbenchCursor.decode(encoded) }.errorCode shouldBe
        WorkbenchErrorCode.REQUEST_INVALID
    }

    "rejects cursor with invalid entry id" {
      val payload =
        WorkbenchCursorPayload(
          at = "2026-07-03T12:00:00Z",
          kind = "activity",
          id = "not-a-uuid",
        )
      val encoded =
        Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(Json.encodeToString(payload).encodeToByteArray())

      shouldThrow<InvalidRequestException> { WorkbenchCursor.decode(encoded) }.errorCode shouldBe
        WorkbenchErrorCode.REQUEST_INVALID
    }

    "entry kind sort rank orders activity before comment" {
      WorkbenchTimelineEntryKind.ACTIVITY.sortRank shouldBe 1
      WorkbenchTimelineEntryKind.COMMENT.sortRank shouldBe 0
    }

    "resolves entry kind wire names" {
      WorkbenchTimelineEntryKind.fromWireName("activity") shouldBe
        WorkbenchTimelineEntryKind.ACTIVITY
      WorkbenchTimelineEntryKind.fromWireName("comment") shouldBe WorkbenchTimelineEntryKind.COMMENT
      WorkbenchTimelineEntryKind.fromWireName("missing").shouldBe(null)
    }
  })
