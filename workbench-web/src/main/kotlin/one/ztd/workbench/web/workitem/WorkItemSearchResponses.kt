package one.ztd.workbench.web.workitem

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
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
import tools.jackson.databind.JsonNode

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
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) override val kind: String = "text",
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val text: String,
) : WorkItemGroupLabelResponse

data class WorkItemGroupLabelMessageResponse(
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) override val kind: String = "message",
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val code: String,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  val args: Map<String, String> = emptyMap(),
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val defaultMessage: String,
) : WorkItemGroupLabelResponse

data class WorkItemSearchGroupBucketResponse(
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val key: JsonObject,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val label: WorkItemGroupLabelResponse,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val count: Long,
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
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  val groups: List<WorkItemSearchGroupBucketResponse>,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
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
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val limit: Int,
  @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val truncated: Boolean,
  @get:Schema(nullable = true) val nextGroupCursor: String?,
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
