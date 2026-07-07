package ink.doa.workbench.web.workitem

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import ink.doa.workbench.core.workitem.model.WorkItemCommentRecord
import ink.doa.workbench.core.workitem.stream.WorkItemEventCodec
import ink.doa.workbench.core.workitem.stream.WorkItemEventRecord
import ink.doa.workbench.core.workitem.timeline.WorkItemTimelineEntry
import ink.doa.workbench.core.workitem.timeline.WorkItemTimelinePage
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonElement

sealed interface WorkItemTimelineEntryResponse {
  val id: String
  val sequence: Long
  val type: String
  val occurredAt: OffsetDateTime

  data class Comment(
    override val id: String,
    override val sequence: Long,
    override val type: String,
    override val occurredAt: OffsetDateTime,
    val author: WorkItemTimelineActorResponse,
    val body: String,
    val editedAt: OffsetDateTime?,
    val commentId: String,
  ) : WorkItemTimelineEntryResponse

  data class Change(
    override val id: String,
    override val sequence: Long,
    override val type: String,
    override val occurredAt: OffsetDateTime,
    val actor: WorkItemTimelineActorResponse?,
    val summary: String?,
    val payload: JsonNode,
  ) : WorkItemTimelineEntryResponse

  companion object {
    fun from(entry: WorkItemTimelineEntry): WorkItemTimelineEntryResponse =
      when (entry) {
        is WorkItemTimelineEntry.Comment -> commentFrom(entry.event, entry.comment)
        is WorkItemTimelineEntry.Event -> changeFrom(entry.record)
      }
  }
}

data class WorkItemTimelineActorResponse(
  val id: String,
  val displayName: String,
)

private fun commentFrom(
  event: WorkItemEventRecord,
  comment: WorkItemCommentRecord,
): WorkItemTimelineEntryResponse.Comment =
  WorkItemTimelineEntryResponse.Comment(
    id = event.apiId.value,
    sequence = event.sequence,
    type = event.eventType.dbValue,
    occurredAt = event.occurredAt,
    author =
      WorkItemTimelineActorResponse(
        id = comment.authorApiId.value,
        displayName = event.actorDisplayName ?: comment.authorApiId.value,
      ),
    body = comment.body,
    editedAt = comment.editedAt,
    commentId = comment.apiId.value,
  )

private fun changeFrom(record: WorkItemEventRecord): WorkItemTimelineEntryResponse.Change {
  val payloadElement = timelineCodec.encodePayload(record.payload)
  val payloadNode =
    timelineObjectMapper.readTree(
      timelineJson.encodeToString(JsonElement.serializer(), payloadElement)
    )
  return WorkItemTimelineEntryResponse.Change(
    id = record.apiId.value,
    sequence = record.sequence,
    type = record.eventType.dbValue,
    occurredAt = record.occurredAt,
    actor =
      record.actorApiId?.let { actorApiId ->
        WorkItemTimelineActorResponse(
          id = actorApiId.value,
          displayName = record.actorDisplayName ?: actorApiId.value,
        )
      },
    summary = record.summary,
    payload = payloadNode,
  )
}

private val timelineCodec = WorkItemEventCodec()
private val timelineObjectMapper = ObjectMapper()
private val timelineJson = WorkItemEventCodec.defaultJson

object WorkItemTimelineResponses {
  fun from(page: WorkItemTimelinePage): List<WorkItemTimelineEntryResponse> =
    page.items.map(WorkItemTimelineEntryResponse::from)
}
