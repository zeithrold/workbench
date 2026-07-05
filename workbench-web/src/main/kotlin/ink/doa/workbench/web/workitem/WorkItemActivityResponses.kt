package ink.doa.workbench.web.workitem

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import ink.doa.workbench.core.workitem.activity.WorkItemActivityCodec
import ink.doa.workbench.core.workitem.activity.WorkItemActivityPageInfo
import ink.doa.workbench.core.workitem.activity.WorkItemActivityRecord
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonElement

data class WorkItemActivityListResponse(
  val items: List<WorkItemActivityResponse>,
  val page: WorkItemActivityPageResponse,
) {
  companion object {
    fun from(
      page: ink.doa.workbench.core.workitem.activity.WorkItemActivityListPage
    ): WorkItemActivityListResponse =
      WorkItemActivityListResponse(
        items = page.items.map(WorkItemActivityResponse::from),
        page = WorkItemActivityPageResponse.from(page.page),
      )
  }
}

data class WorkItemActivityPageResponse(
  val limit: Int,
  val nextBefore: OffsetDateTime?,
) {
  companion object {
    fun from(page: WorkItemActivityPageInfo): WorkItemActivityPageResponse =
      WorkItemActivityPageResponse(limit = page.limit, nextBefore = page.nextBefore)
  }
}

data class WorkItemActivityResponse(
  val id: String,
  val type: String,
  val occurredAt: OffsetDateTime,
  val actor: WorkItemActivityActorResponse?,
  val summary: String?,
  val payload: JsonNode,
) {
  companion object {
    private val codec = WorkItemActivityCodec()
    private val objectMapper = ObjectMapper()
    private val json = WorkItemActivityCodec.defaultJson

    fun from(record: WorkItemActivityRecord): WorkItemActivityResponse {
      val payloadElement = codec.encodePayload(record.payload)
      val payloadNode =
        objectMapper.readTree(json.encodeToString(JsonElement.serializer(), payloadElement))
      return WorkItemActivityResponse(
        id = record.apiId.value,
        type = record.activityType.dbValue,
        occurredAt = record.occurredAt,
        actor =
          record.actorApiId?.let { actorApiId ->
            WorkItemActivityActorResponse(
              id = actorApiId.value,
              displayName = record.actorDisplayName ?: actorApiId.value,
            )
          },
        summary = record.summary,
        payload = payloadNode,
      )
    }
  }
}

data class WorkItemActivityActorResponse(
  val id: String,
  val displayName: String,
)
