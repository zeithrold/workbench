package one.ztd.workbench.web.workitem

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import one.ztd.workbench.agile.workitem.model.WorkItemSearchGroupBucket
import one.ztd.workbench.agile.workitem.model.WorkItemSearchGroupsPage
import one.ztd.workbench.agile.workitem.query.QueryValue
import one.ztd.workbench.agile.workitem.query.WorkItemGroupKey
import one.ztd.workbench.agile.workitem.query.WorkItemGroupLabel

data class WorkItemSearchRequest(
  @field:NotNull @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val query: JsonNode,
  val scope: JsonNode? = null,
  val limit: Int? = null,
  val cursor: String? = null,
)

data class WorkItemSearchGroupsRequest(
  @field:NotNull @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val query: JsonNode,
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

internal fun WorkItemGroupKey.toJsonObject(): JsonObject = buildJsonObject {
  put("field", field.canonicalName)
  put("op", op.wireName)
  value?.let { queryValue ->
    when (queryValue) {
      is QueryValue.Literal -> put("value", queryValue.value)
      else -> Unit
    }
  }
}
