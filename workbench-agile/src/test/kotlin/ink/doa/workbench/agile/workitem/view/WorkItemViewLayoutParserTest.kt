package ink.doa.workbench.agile.workitem.view

import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WorkItemViewLayoutParserTest :
  FunSpec({
    val parser = WorkItemViewLayoutParser()

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

    test("defaults expose empty layout payloads") {
      WorkItemViewDefaults.EMPTY_QUERY shouldBe
        JsonObject(
          mapOf(
            "version" to kotlinx.serialization.json.JsonPrimitive(1),
            "resource" to kotlinx.serialization.json.JsonPrimitive("work_item"),
            "sort" to JsonArray(emptyList()),
          )
        )
      WorkItemViewDefaults.EMPTY_DISPLAY_FIELDS shouldBe JsonArray(emptyList())
    }

    test("returns empty list for null display fields") {
      parser.parseDisplayFields(JsonNull) shouldBe emptyList()
    }

    test("parses width and pinned display field options") {
      val displayFields = buildJsonArray {
        add(
          buildJsonObject {
            put(
              "field",
              buildJsonObject {
                put("kind", "property")
                put("code", "severity")
              },
            )
            put("width", 240)
            put("pinned", true)
          }
        )
      }

      val parsed = parser.parseDisplayFields(displayFields).single()
      parsed.field.canonicalName shouldBe "property.severity"
      parsed.width shouldBe 240
      parsed.pinned shouldBe true
    }

    test("rejects non-integer display field width") {
      val displayFields = buildJsonArray {
        add(
          buildJsonObject {
            put(
              "field",
              buildJsonObject {
                put("kind", "property")
                put("code", "severity")
              },
            )
            put("width", "wide")
          }
        )
      }

      shouldThrow<InvalidRequestException> { parser.parseDisplayFields(displayFields) }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_VIEW_DISPLAY_FIELD_WIDTH_INVALID
    }

    test("rejects non-array display fields payload") {
      shouldThrow<InvalidRequestException> { parser.parseDisplayFields(JsonObject(emptyMap())) }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_VIEW_LAYOUT_ARRAY_REQUIRED
    }
  })
