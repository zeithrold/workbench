package ink.doa.workbench.core.common.pagination

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable data class WorkItemSearchGroupCursorPayload(val value: JsonElement)

data class WorkItemSearchGroupCursor(val value: JsonElement) {
  fun encode(): String {
    val payload = WorkItemSearchGroupCursorPayload(value = value)
    return Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(json.encodeToString(payload).encodeToByteArray())
  }

  companion object {
    private val json = Json { ignoreUnknownKeys = true }

    private fun invalidCursor(): Nothing =
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_SEARCH_GROUP_CURSOR_INVALID,
        "Invalid work item search group cursor.",
      )

    fun decode(value: String): WorkItemSearchGroupCursor {
      val decoded =
        runCatching {
            val jsonText = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
            json.decodeFromString<WorkItemSearchGroupCursorPayload>(jsonText)
          }
          .getOrNull() ?: invalidCursor()
      return WorkItemSearchGroupCursor(value = decoded.value)
    }
  }
}
