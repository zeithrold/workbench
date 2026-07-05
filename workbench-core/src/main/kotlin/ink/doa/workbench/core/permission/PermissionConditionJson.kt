package ink.doa.workbench.core.permission

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.query.WorkItemConditionJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

object PermissionConditionJson {
  private val json = Json { ignoreUnknownKeys = true }

  fun validateAndCanonicalize(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val element =
      try {
        json.parseToJsonElement(raw.trim())
      } catch (_: Exception) {
        throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_RULE_CONDITION_INVALID)
      }
    if (element !is JsonObject) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_RULE_CONDITION_INVALID)
    }
    val canonical = WorkItemConditionJson.canonicalize(element)
    if (canonical.isEmpty()) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_RULE_CONDITION_INVALID)
    }
    WorkItemConditionJson.parse(canonical)
      ?: throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_RULE_CONDITION_INVALID)
    return canonical.toString()
  }
}
