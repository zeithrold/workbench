package ink.doa.workbench.web.workitem

import com.fasterxml.jackson.databind.JsonNode
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal fun JsonNode?.toJsonElement(default: JsonElement): JsonElement =
  this?.let { Json.parseToJsonElement(it.toString()) } ?: default

internal fun JsonNode?.toJsonElement(): JsonElement =
  this?.let { Json.parseToJsonElement(it.toString()) }
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_VIEW_LAYOUT_FIELD_REQUIRED,
      "Work item view JSON field is required.",
    )

internal fun actorUserId(projectContext: ink.doa.workbench.web.api.context.ProjectRequestContext) =
  projectContext.actor?.id
    ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
