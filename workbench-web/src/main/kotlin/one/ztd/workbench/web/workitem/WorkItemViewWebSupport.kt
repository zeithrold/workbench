package one.ztd.workbench.web.workitem

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode

internal fun JsonNode?.toJsonElement(default: JsonElement): JsonElement =
  this?.let { Json.parseToJsonElement(it.toString()) } ?: default

internal fun JsonNode?.toJsonElement(): JsonElement =
  this?.let { Json.parseToJsonElement(it.toString()) }
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_VIEW_LAYOUT_FIELD_REQUIRED,
      "Work item view JSON field is required.",
    )

internal fun actorUserId(projectContext: one.ztd.workbench.web.api.context.ProjectRequestContext) =
  projectContext.actor?.id
    ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
