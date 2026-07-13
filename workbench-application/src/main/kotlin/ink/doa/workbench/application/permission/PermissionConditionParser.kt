package ink.doa.workbench.application.permission

import ink.doa.workbench.agile.workitem.query.ConditionNode
import ink.doa.workbench.agile.workitem.query.WorkItemConditionJson
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object PermissionConditionParser {
  private val json = Json { ignoreUnknownKeys = true }

  fun parseOrNull(element: JsonObject): ConditionNode? {
    if (element.isEmpty()) return null
    return try {
      WorkItemConditionJson.parse(element)
    } catch (_: Exception) {
      null
    }
  }

  fun canonicalizeOrThrow(raw: String): String {
    val element =
      try {
        json.parseToJsonElement(raw.trim())
      } catch (_: Exception) {
        invalidCondition()
      }
    if (element !is JsonObject) invalidCondition()
    val canonical =
      try {
        val canonicalized = WorkItemConditionJson.canonicalize(element)
        if (canonicalized.isEmpty() || WorkItemConditionJson.parse(canonicalized) == null) {
          invalidCondition()
        }
        canonicalized
      } catch (_: InvalidRequestException) {
        invalidCondition()
      } catch (_: Exception) {
        invalidCondition()
      }
    return canonical.toString()
  }

  private fun invalidCondition(): Nothing {
    throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_RULE_CONDITION_INVALID)
  }
}
