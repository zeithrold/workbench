package ink.doa.workbench.core.common.pagination

import ink.doa.workbench.core.common.errors.InvalidRequestException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID

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
