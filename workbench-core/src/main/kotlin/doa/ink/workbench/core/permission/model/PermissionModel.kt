package doa.ink.workbench.core.permission.model

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

interface PermissionService {
  suspend fun decide(request: AuthorizationRequest): AuthorizationDecision
}
