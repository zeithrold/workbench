package ink.doa.workbench.core.common.pagination

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

class WorkItemSearchGroupCursorTest :
  StringSpec({
    "round-trips group bucket value" {
      val original = WorkItemSearchGroupCursor(value = JsonPrimitive("todo"))

      WorkItemSearchGroupCursor.decode(original.encode()) shouldBe original
    }

    "round-trips null group bucket value" {
      val original = WorkItemSearchGroupCursor(value = JsonNull)

      WorkItemSearchGroupCursor.decode(original.encode()) shouldBe original
    }

    "rejects malformed encoded cursor" {
      shouldThrow<InvalidRequestException> { WorkItemSearchGroupCursor.decode("not-a-cursor") }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_SEARCH_GROUP_CURSOR_INVALID
    }
  })
