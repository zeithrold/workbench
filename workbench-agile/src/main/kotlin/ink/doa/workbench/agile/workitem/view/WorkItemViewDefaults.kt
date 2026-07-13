package ink.doa.workbench.agile.workitem.view

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

object WorkItemViewDefaults {
  val EMPTY_QUERY =
    JsonObject(
      mapOf(
        "version" to kotlinx.serialization.json.JsonPrimitive(1),
        "resource" to kotlinx.serialization.json.JsonPrimitive("work_item"),
        "sort" to JsonArray(emptyList()),
      )
    )
  val EMPTY_DISPLAY_FIELDS = JsonArray(emptyList())
}
