package ink.doa.workbench.web.workitem

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import ink.doa.workbench.core.workitem.activity.WorkItemActivityCodec
import ink.doa.workbench.core.workitem.activity.WorkItemActivityRecord
import ink.doa.workbench.core.workitem.model.WorkItemCommentRecord
import ink.doa.workbench.core.workitem.timeline.WorkItemTimelineEntry
import ink.doa.workbench.core.workitem.timeline.WorkItemTimelinePage
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonElement

sealed interface WorkItemTimelineEntryResponse {
  val kind: String
  val id: String
  val occurredAt: OffsetDateTime

  data class Comment(
    override val id: String,
    override val occurredAt: OffsetDateTime,
    val author: WorkItemTimelineActorResponse,
    val body: String,
    val editedAt: OffsetDateTime?,
  ) : WorkItemTimelineEntryResponse {
    override val kind: String = "comment"
  }

  data class Activity(
    override val id: String,
    override val occurredAt: OffsetDateTime,
    val type: String,
    val actor: WorkItemTimelineActorResponse?,
    val summary: String?,
    val payload: JsonNode,
  ) : WorkItemTimelineEntryResponse {
    override val kind: String = "activity"
  }

  companion object {
    fun from(entry: WorkItemTimelineEntry): WorkItemTimelineEntryResponse =
      when (entry) {
        is WorkItemTimelineEntry.Activity -> activityFrom(entry.record)
        is WorkItemTimelineEntry.Comment -> commentFrom(entry.record)
      }
  }
}

data class WorkItemTimelineActorResponse(
  val id: String,
  val displayName: String,
)

private fun commentFrom(record: WorkItemCommentRecord): WorkItemTimelineEntryResponse.Comment =
  WorkItemTimelineEntryResponse.Comment(
    id = record.apiId.value,
    occurredAt = record.createdAt,
    author =
      WorkItemTimelineActorResponse(
        id = record.authorApiId.value,
        displayName = record.authorApiId.value,
      ),
    body = record.body,
    editedAt = record.editedAt,
  )

private fun activityFrom(record: WorkItemActivityRecord): WorkItemTimelineEntryResponse.Activity {
  val payloadElement = timelineCodec.encodePayload(record.payload)
  val payloadNode =
    timelineObjectMapper.readTree(
      timelineJson.encodeToString(JsonElement.serializer(), payloadElement)
    )
  return WorkItemTimelineEntryResponse.Activity(
    id = record.apiId.value,
    occurredAt = record.occurredAt,
    type = record.activityType.dbValue,
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

private val timelineCodec = WorkItemActivityCodec()
private val timelineObjectMapper = ObjectMapper()
private val timelineJson = WorkItemActivityCodec.defaultJson

object WorkItemTimelineResponses {
  fun from(page: WorkItemTimelinePage): List<WorkItemTimelineEntryResponse> =
    page.items.map(WorkItemTimelineEntryResponse::from)
}
