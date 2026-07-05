package ink.doa.workbench.core.workitem.view

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WorkItemViewConfigurationValidatorTest :
  FunSpec({
    val validator = WorkItemViewConfigurationValidator()
    val layoutParser = WorkItemViewLayoutParser()

    test("tenant-scoped view rejects project visibility") {
      shouldThrow<InvalidRequestException> {
          validator.validateVisibility(projectId = null, WorkItemViewVisibility.PROJECT)
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_VIEW_VISIBILITY_PROJECT_FORBIDDEN
    }

    test("project-scoped view accepts project visibility") {
      validator.validateVisibility(UUID.randomUUID(), WorkItemViewVisibility.PROJECT)
    }

    test("validates filter sort group and display fields") {
      val filter = buildJsonObject {
        put("field", "status")
        put("op", "eq")
        put("value", "done")
      }
      val sort = buildJsonArray {
        add(
          buildJsonObject {
            put("field", "title")
            put("direction", "asc")
          }
        )
      }
      val group = buildJsonObject {
        put("field", "statusGroup")
        put("direction", "asc")
        put("collapsed", buildJsonArray { add(JsonPrimitive("done")) })
      }
      val displayFields = buildJsonArray {
        add(
          buildJsonObject {
            put("field", "key")
            put("width", 120)
            put("pinned", true)
          }
        )
      }

      validator.validateLayout(filter, sort, group, displayFields)
      layoutParser.parseGroup(group)?.field?.canonicalName shouldBe "statusGroup"
      layoutParser.parseDisplayFields(displayFields).single().width shouldBe 120
    }

    test("empty filter and sort are allowed") {
      validator.validateLayout(
        JsonObject(emptyMap()),
        JsonArray(emptyList()),
        JsonObject(emptyMap()),
        JsonArray(emptyList()),
      )
    }

    test("rejects unknown visibility values") {
      shouldThrow<InvalidRequestException> { WorkItemViewVisibility.fromDbValue("shared") }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_VIEW_VISIBILITY_UNKNOWN
    }

    test("parses empty group and display field lists") {
      layoutParser.parseGroup(JsonObject(emptyMap())).shouldBeNull()
      layoutParser.parseDisplayFields(JsonArray(emptyList())) shouldBe emptyList()
    }

    test("round-trips visibility db values") {
      WorkItemViewVisibility.fromDbValue("private") shouldBe WorkItemViewVisibility.PRIVATE
      WorkItemViewVisibility.fromDbValue("PROJECT") shouldBe WorkItemViewVisibility.PROJECT
      WorkItemViewVisibility.fromDbValue("tenant") shouldBe WorkItemViewVisibility.TENANT
    }

    test("rejects invalid filter operators") {
      val filter = buildJsonObject {
        put("field", "title")
        put("op", "magic")
        put("value", "x")
      }

      shouldThrow<InvalidRequestException> {
          validator.validateLayout(
            filter,
            JsonArray(emptyList()),
            JsonObject(emptyMap()),
            JsonArray(emptyList()),
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_UNKNOWN
    }

    test("rejects invalid sort direction") {
      val sort = buildJsonArray {
        add(
          buildJsonObject {
            put("field", "title")
            put("direction", "sideways")
          }
        )
      }

      shouldThrow<InvalidRequestException> {
          validator.validateLayout(
            JsonObject(emptyMap()),
            sort,
            JsonObject(emptyMap()),
            JsonArray(emptyList()),
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_QUERY_SORT_DIRECTION_UNKNOWN
    }
  })
