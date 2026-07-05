package ink.doa.workbench.core.workitem.view

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.query.WorkItemQueryParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

class WorkItemViewLayoutParser(
  private val queryParser: WorkItemQueryParser = WorkItemQueryParser()
) {
  fun parseDisplayFields(element: JsonElement): List<WorkItemViewDisplayField> {
    if (element is JsonNull) return emptyList()
    return element.asArray("display fields").map { item ->
      val obj = item.asObject("display field")
      val width =
        obj["width"]?.jsonPrimitive?.intOrNull
          ?: obj["width"]?.let {
            throw InvalidRequestException(
              WorkbenchErrorCode.WORK_ITEM_VIEW_DISPLAY_FIELD_WIDTH_INVALID,
              "Work item view display field width must be an integer.",
            )
          }
      val pinned = obj["pinned"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
      WorkItemViewDisplayField(
        field = queryParser.parseField(obj.required("field")),
        width = width,
        pinned = pinned,
      )
    }
  }
}

private fun JsonElement.asObject(name: String): JsonObject =
  this as? JsonObject
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_VIEW_LAYOUT_OBJECT_REQUIRED,
      "Work item view $name must be an object.",
    )

private fun JsonElement.asArray(name: String): JsonArray =
  this as? JsonArray
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_VIEW_LAYOUT_ARRAY_REQUIRED,
      "Work item view $name must be an array.",
    )

private fun JsonObject.required(key: String): JsonElement =
  this[key]
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_VIEW_LAYOUT_FIELD_REQUIRED,
      "Work item view layout missing required field: $key",
    )
