package ink.doa.workbench.core.permission

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.query.WorkItemConditionJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object PermissionConditionJson {
  private val json = Json { ignoreUnknownKeys = true }

  fun validateAndCanonicalize(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val element =
      try {
        json.parseToJsonElement(raw.trim())
      } catch (_: Exception) {
        return invalidCondition()
      }
    if (element !is JsonObject) return invalidCondition()
    val canonical = WorkItemConditionJson.canonicalize(element)
    if (canonical.isEmpty() || WorkItemConditionJson.parse(canonical) == null) {
      return invalidCondition()
    }
    return canonical.toString()
  }

  private fun invalidCondition(): Nothing {
    throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_RULE_CONDITION_INVALID)
  }
}
