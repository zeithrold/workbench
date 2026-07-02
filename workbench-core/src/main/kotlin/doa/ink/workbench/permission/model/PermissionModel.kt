package doa.ink.workbench.permission.model

import doa.ink.workbench.identity.model.UserRecord

enum class PermissionEffect {
  ALLOW,
  DENY,
}

@JvmInline
value class PermissionAction(val code: String) {
  init {
    require(code.matches(Regex("^[a-z]+(\\.[a-z]+)+$"))) {
      "Permission action must be dot-separated lower-case words."
    }
  }
}

data class ResourcePattern(val value: String)

sealed interface PermissionCondition {
  data class FieldEquals(val field: String, val expected: String) : PermissionCondition

  data class AllOf(val conditions: List<PermissionCondition>) : PermissionCondition
}

data class PermissionRule(
  val effect: PermissionEffect,
  val actions: Set<PermissionAction>,
  val resource: ResourcePattern,
  val condition: PermissionCondition?,
)

data class PermissionContext(val projectApiId: String?, val issueApiId: String?)

sealed interface PermissionDecision {
  data object Allowed : PermissionDecision

  data class Denied(val reason: String) : PermissionDecision
}

interface PermissionService {
  suspend fun can(
    actor: UserRecord,
    action: PermissionAction,
    resource: ResourcePattern,
    context: PermissionContext,
  ): PermissionDecision
}
