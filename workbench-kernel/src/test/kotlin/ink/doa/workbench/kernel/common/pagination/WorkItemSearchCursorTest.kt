package ink.doa.workbench.kernel.common.pagination

import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive

class WorkItemSearchCursorTest :
  StringSpec({
    "round-trips sort values and api id" {
      val original =
        WorkItemSearchCursor(
          sortValues = listOf(JsonPrimitive("todo"), JsonPrimitive("2026-01-01T00:00:00Z")),
          apiId = "iss_01JABCDEFGHJKMNPQRSTVWXYZ0",
        )

      WorkItemSearchCursor.decode(original.encode()) shouldBe original
    }

    "rejects blank api id on decode" {
      val token =
        WorkItemSearchCursor(sortValues = listOf(JsonPrimitive("todo")), apiId = " ").encode()

      shouldThrow<InvalidRequestException> { WorkItemSearchCursor.decode(token) }.errorCode shouldBe
        WorkbenchErrorCode.WORK_ITEM_SEARCH_CURSOR_INVALID
    }

    "rejects malformed encoded cursor" {
      shouldThrow<InvalidRequestException> { WorkItemSearchCursor.decode("not-a-cursor") }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_SEARCH_CURSOR_INVALID
    }
  })
