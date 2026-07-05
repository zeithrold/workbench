package ink.doa.workbench.core.workitem.view

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

object WorkItemViewDefaults {
  val EMPTY_FILTER = JsonObject(emptyMap())
  val EMPTY_SORT = JsonArray(emptyList())
  val EMPTY_GROUP = JsonObject(emptyMap())
  val EMPTY_DISPLAY_FIELDS = JsonArray(emptyList())
}
