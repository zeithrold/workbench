package ink.doa.workbench.web.workitem

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.JsonNode
import ink.doa.workbench.agile.workitem.model.WorkItemSearchGroupBucket
import ink.doa.workbench.agile.workitem.model.WorkItemSearchGroupsPage
import ink.doa.workbench.agile.workitem.model.WorkItemSearchHit
import ink.doa.workbench.agile.workitem.query.QueryValue
import ink.doa.workbench.agile.workitem.query.WorkItemGroupKey
import ink.doa.workbench.agile.workitem.query.WorkItemGroupLabel
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class WorkItemSearchRequest(
  val query: JsonNode,
  val scope: JsonNode? = null,
  val limit: Int? = null,
  val cursor: String? = null,
)

data class WorkItemSearchGroupsRequest(
  val query: JsonNode,
  val groupLimit: Int? = null,
  val groupCursor: String? = null,
)

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.EXISTING_PROPERTY,
  property = "kind",
)
@JsonSubTypes(
  JsonSubTypes.Type(value = WorkItemGroupLabelTextResponse::class, name = "text"),
  JsonSubTypes.Type(value = WorkItemGroupLabelMessageResponse::class, name = "message"),
)
sealed interface WorkItemGroupLabelResponse {
  val kind: String

  companion object {
    fun from(label: WorkItemGroupLabel): WorkItemGroupLabelResponse =
      when (label) {
        is WorkItemGroupLabel.Text -> WorkItemGroupLabelTextResponse(text = label.text)
        is WorkItemGroupLabel.Message ->
          WorkItemGroupLabelMessageResponse(
            code = label.code,
            args = label.args,
            defaultMessage = label.defaultMessage,
          )
      }
  }
}

data class WorkItemGroupLabelTextResponse(
  override val kind: String = "text",
  val text: String,
) : WorkItemGroupLabelResponse

data class WorkItemGroupLabelMessageResponse(
  override val kind: String = "message",
  val code: String,
  val args: Map<String, String> = emptyMap(),
  val defaultMessage: String,
) : WorkItemGroupLabelResponse

data class WorkItemSearchHitResponse(
  val id: String,
  val key: String,
  val title: String,
  val description: RichTextDocumentPayload?,
  val projectId: String,
  val issueTypeId: String,
  val issueTypeConfigId: String,
  val statusId: String,
  val statusGroup: String,
  val priorityId: String?,
  val reporterId: String,
  val assigneeId: String?,
  val sprintId: String?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
  val properties: JsonObject,
  val groupKey: JsonObject? = null,
  val groupLabel: WorkItemGroupLabelResponse? = null,
) {
  companion object {
    fun from(hit: WorkItemSearchHit): WorkItemSearchHitResponse =
      WorkItemSearchHitResponse(
        id = hit.apiId,
        key = hit.key,
        title = hit.title,
        description = hit.description?.let(RichTextDocumentPayload::from),
        projectId = hit.projectApiId,
        issueTypeId = hit.issueTypeApiId,
        issueTypeConfigId = hit.issueTypeConfigApiId,
        statusId = hit.statusApiId,
        statusGroup = hit.statusGroup,
        priorityId = hit.priorityApiId,
        reporterId = hit.reporterApiId,
        assigneeId = hit.assigneeApiId,
        sprintId = hit.sprintApiId,
        createdAt = hit.createdAt,
        updatedAt = hit.updatedAt,
        properties = hit.properties,
        groupKey = hit.groupKey?.toJsonObject(),
        groupLabel = hit.groupLabel?.let(WorkItemGroupLabelResponse::from),
      )
  }
}

data class WorkItemSearchGroupBucketResponse(
  val key: JsonObject,
  val label: WorkItemGroupLabelResponse,
  val count: Long,
) {
  companion object {
    fun from(bucket: WorkItemSearchGroupBucket): WorkItemSearchGroupBucketResponse =
      WorkItemSearchGroupBucketResponse(
        key = bucket.key.toJsonObject(),
        label = WorkItemGroupLabelResponse.from(bucket.label),
        count = bucket.count,
      )
  }
}

data class WorkItemSearchGroupsPageResponse(
  val groups: List<WorkItemSearchGroupBucketResponse>,
  val groupsPage: WorkItemSearchGroupsPageInfoResponse,
) {
  companion object {
    fun from(page: WorkItemSearchGroupsPage): WorkItemSearchGroupsPageResponse =
      WorkItemSearchGroupsPageResponse(
        groups = page.groups.map(WorkItemSearchGroupBucketResponse::from),
        groupsPage =
          WorkItemSearchGroupsPageInfoResponse(
            limit = page.groups.size,
            truncated = page.truncated,
            nextGroupCursor = page.nextGroupCursor?.encode(),
          ),
      )
  }
}

data class WorkItemSearchGroupsPageInfoResponse(
  val limit: Int,
  val truncated: Boolean,
  val nextGroupCursor: String?,
)

private fun WorkItemGroupKey.toJsonObject(): JsonObject = buildJsonObject {
  put("field", field.canonicalName)
  put("op", op.wireName)
  value?.let { queryValue ->
    when (queryValue) {
      is QueryValue.Literal -> put("value", queryValue.value)
      else -> Unit
    }
  }
}
