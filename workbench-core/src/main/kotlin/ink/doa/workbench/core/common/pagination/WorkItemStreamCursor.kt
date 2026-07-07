package ink.doa.workbench.core.common.pagination

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class WorkItemStreamCursorPayload(val sequence: Long)

data class WorkItemStreamCursor(val sequence: Long) {
  fun encode(): String {
    val payload = WorkItemStreamCursorPayload(sequence = sequence)
    return Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(Json.encodeToString(payload).encodeToByteArray())
  }

  companion object {
    private val json = Json { ignoreUnknownKeys = true }

    private fun invalidCursor(): Nothing =
      throw InvalidRequestException(WorkbenchErrorCode.REQUEST_INVALID, "Invalid cursor.")

    fun decode(value: String): WorkItemStreamCursor {
      val decoded =
        runCatching {
            val jsonText = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
            json.decodeFromString<WorkItemStreamCursorPayload>(jsonText)
          }
          .getOrNull() ?: invalidCursor()
      if (decoded.sequence < 1) invalidCursor()
      return WorkItemStreamCursor(sequence = decoded.sequence)
    }
  }
}
