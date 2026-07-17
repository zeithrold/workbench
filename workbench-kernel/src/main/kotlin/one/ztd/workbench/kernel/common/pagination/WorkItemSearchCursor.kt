package one.ztd.workbench.kernel.common.pagination

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode

@Serializable
data class WorkItemSearchCursorPayload(
  val sort: List<JsonElement>,
  val id: String,
)

data class WorkItemSearchCursor(
  val sortValues: List<JsonElement>,
  val apiId: String,
) {
  fun encode(): String {
    val payload = WorkItemSearchCursorPayload(sort = sortValues, id = apiId)
    return Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(json.encodeToString(payload).encodeToByteArray())
  }

  companion object {
    private val json = Json { ignoreUnknownKeys = true }

    private fun invalidCursor(): Nothing =
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_SEARCH_CURSOR_INVALID,
        "Invalid work item search cursor.",
      )

    fun decode(value: String): WorkItemSearchCursor {
      val decoded =
        runCatching {
            val jsonText = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
            json.decodeFromString<WorkItemSearchCursorPayload>(jsonText)
          }
          .getOrNull() ?: invalidCursor()
      if (decoded.sort.isEmpty() || decoded.id.isBlank()) invalidCursor()
      return WorkItemSearchCursor(sortValues = decoded.sort, apiId = decoded.id)
    }
  }
}
