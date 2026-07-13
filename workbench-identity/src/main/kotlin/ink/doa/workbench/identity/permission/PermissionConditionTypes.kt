package ink.doa.workbench.identity.permission

enum class PermissionConditionResult {
  MATCH,
  NO_MATCH,
  INVALID,
}

data class PermissionConditionContext(
  val actorUserApiId: String,
  val resourceAttributes: Map<String, String> = emptyMap(),
)
