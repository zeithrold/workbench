package one.ztd.workbench.agile.workitem.view

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode

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

    test("validates query and display fields") {
      val query = buildJsonObject {
        put("version", 1)
        put("resource", "work_item")
        put(
          "where",
          buildJsonObject {
            put("field", "status")
            put("op", "eq")
            put("value", "done")
          },
        )
        put(
          "sort",
          buildJsonArray {
            add(
              buildJsonObject {
                put("field", "title")
                put("direction", "asc")
              }
            )
          },
        )
        put(
          "group",
          buildJsonObject {
            put("field", "statusGroup")
            put("direction", "asc")
          },
        )
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

      validator.validateLayout(query, displayFields)
      layoutParser.parseDisplayFields(displayFields).single().width shouldBe 120
    }

    test("empty query sort is allowed") {
      validator.validateLayout(WorkItemViewDefaults.EMPTY_QUERY, JsonArray(emptyList()))
    }

    test("rejects unknown visibility values") {
      shouldThrow<InvalidRequestException> { WorkItemViewVisibility.fromDbValue("shared") }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_VIEW_VISIBILITY_UNKNOWN
    }

    test("parses empty display field lists") {
      layoutParser.parseDisplayFields(JsonArray(emptyList())) shouldBe emptyList()
    }

    test("round-trips visibility db values") {
      WorkItemViewVisibility.fromDbValue("private") shouldBe WorkItemViewVisibility.PRIVATE
      WorkItemViewVisibility.fromDbValue("PROJECT") shouldBe WorkItemViewVisibility.PROJECT
      WorkItemViewVisibility.fromDbValue("tenant") shouldBe WorkItemViewVisibility.TENANT
    }

    test("rejects invalid filter operators in query") {
      val query = buildJsonObject {
        put("version", 1)
        put("resource", "work_item")
        put(
          "where",
          buildJsonObject {
            put("field", "title")
            put("op", "magic")
            put("value", "x")
          },
        )
      }

      shouldThrow<InvalidRequestException> {
          validator.validateLayout(query, JsonArray(emptyList()))
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_UNKNOWN
    }

    test("rejects invalid sort direction in query") {
      val query = buildJsonObject {
        put("version", 1)
        put("resource", "work_item")
        put(
          "sort",
          buildJsonArray {
            add(
              buildJsonObject {
                put("field", "title")
                put("direction", "sideways")
              }
            )
          },
        )
      }

      shouldThrow<InvalidRequestException> {
          validator.validateLayout(query, JsonArray(emptyList()))
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_QUERY_SORT_DIRECTION_UNKNOWN
    }
  })
