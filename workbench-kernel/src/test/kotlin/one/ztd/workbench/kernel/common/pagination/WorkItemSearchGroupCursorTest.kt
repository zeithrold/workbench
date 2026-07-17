package one.ztd.workbench.kernel.common.pagination

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode

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
