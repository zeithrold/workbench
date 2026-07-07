package ink.doa.workbench.core.permission

import java.util.UUID

enum class PermissionConditionResult {
  MATCH,
  NO_MATCH,
  INVALID,
}

data class PermissionConditionContext(
  val actorUserId: UUID,
  val resourceAttributes: Map<String, String> = emptyMap(),
)
