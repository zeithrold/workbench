package ink.doa.workbench.core.workitem.view

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WorkItemViewLayoutParserTest :
  FunSpec({
    val parser = WorkItemViewLayoutParser()

    test("returns null for empty or null group payloads") {
      parser.parseGroup(JsonObject(emptyMap())).shouldBeNull()
      parser.parseGroup(JsonNull).shouldBeNull()
    }

    test("parses property field references in display fields") {
      val displayFields = buildJsonArray {
        add(
          buildJsonObject {
            put(
              "field",
              buildJsonObject {
                put("kind", "property")
                put("code", "storyPoints")
              },
            )
          }
        )
      }

      parser.parseDisplayFields(displayFields).single().field.canonicalName shouldBe
        "property.storyPoints"
    }

    test("rejects invalid group direction") {
      val group = buildJsonObject {
        put("field", "status")
        put("direction", "sideways")
      }

      shouldThrow<InvalidRequestException> { parser.parseGroup(group) }.errorCode shouldBe
        WorkbenchErrorCode.WORK_ITEM_VIEW_GROUP_DIRECTION_UNKNOWN
    }

    test("rejects non-integer display field width") {
      val displayFields = buildJsonArray {
        add(
          buildJsonObject {
            put("field", "title")
            put("width", "wide")
          }
        )
      }

      shouldThrow<InvalidRequestException> { parser.parseDisplayFields(displayFields) }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_VIEW_DISPLAY_FIELD_WIDTH_INVALID
    }

    test("rejects malformed layout payloads") {
      shouldThrow<InvalidRequestException> { parser.parseGroup(JsonPrimitive("status")) }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_VIEW_LAYOUT_OBJECT_REQUIRED
      shouldThrow<InvalidRequestException> { parser.parseDisplayFields(JsonObject(emptyMap())) }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_VIEW_LAYOUT_ARRAY_REQUIRED
      shouldThrow<InvalidRequestException> {
          parser.parseGroup(buildJsonObject { put("direction", "asc") })
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_VIEW_LAYOUT_FIELD_REQUIRED
    }

    test("defaults expose empty layout payloads") {
      WorkItemViewDefaults.EMPTY_FILTER shouldBe JsonObject(emptyMap())
      WorkItemViewDefaults.EMPTY_SORT shouldBe JsonArray(emptyList())
      WorkItemViewDefaults.EMPTY_GROUP shouldBe JsonObject(emptyMap())
      WorkItemViewDefaults.EMPTY_DISPLAY_FIELDS shouldBe JsonArray(emptyList())
    }
  })
