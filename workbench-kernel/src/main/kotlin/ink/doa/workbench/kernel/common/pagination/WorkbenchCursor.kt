package ink.doa.workbench.kernel.common.pagination

import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WorkbenchCursorPayload(
  val at: String,
  val kind: String,
  val id: String,
)

data class WorkbenchCursor(
  val occurredAt: OffsetDateTime,
  val entryKind: WorkbenchTimelineEntryKind,
  val entryId: UUID,
) {
  fun encode(): String {
    val payload =
      WorkbenchCursorPayload(
        at = occurredAt.toString(),
        kind = entryKind.wireName,
        id = entryId.toString(),
      )
    return Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(Json.encodeToString(payload).encodeToByteArray())
  }

  companion object {
    private val json = Json { ignoreUnknownKeys = true }

    private fun invalidCursor(): Nothing =
      throw InvalidRequestException(WorkbenchErrorCode.REQUEST_INVALID, "Invalid cursor.")

    fun decode(value: String): WorkbenchCursor {
      val decoded =
        runCatching {
            val jsonText = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
            json.decodeFromString<WorkbenchCursorPayload>(jsonText)
          }
          .getOrNull() ?: invalidCursor()
      val occurredAt =
        runCatching { OffsetDateTime.parse(decoded.at) }.getOrNull() ?: invalidCursor()
      val entryKind = WorkbenchTimelineEntryKind.fromWireName(decoded.kind) ?: invalidCursor()
      val entryId = runCatching { UUID.fromString(decoded.id) }.getOrNull() ?: invalidCursor()
      return WorkbenchCursor(
        occurredAt = occurredAt,
        entryKind = entryKind,
        entryId = entryId,
      )
    }
  }
}

enum class WorkbenchTimelineEntryKind(val wireName: String, val sortRank: Int) {
  ACTIVITY("activity", 1),
  COMMENT("comment", 0);

  companion object {
    fun fromWireName(value: String): WorkbenchTimelineEntryKind? = entries.firstOrNull {
      it.wireName == value
    }
  }
}
