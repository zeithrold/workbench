package ink.doa.workbench.core.workitem.access

import ink.doa.workbench.core.workitem.model.WorkItemRecord
import java.util.UUID
import kotlinx.serialization.json.JsonElement

data class AccessConditionContext(
  val actorUserId: UUID,
  val actorGroupIds: Set<UUID> = emptySet(),
  val workItem: WorkItemRecord? = null,
  val properties: Map<String, JsonElement> = emptyMap(),
  val childIssuesNotDone: Long = 0,
  val resourceAttributes: Map<String, String> = emptyMap(),
) {
  companion object {
    fun fromEvaluation(context: WorkItemAccessEvaluationContext): AccessConditionContext =
      AccessConditionContext(
        actorUserId = context.actor.userId,
        actorGroupIds = context.actor.groupIds,
        workItem = context.workItem,
        properties = context.properties,
        childIssuesNotDone = context.childIssuesNotDone,
      )

    fun fromResourceAttributes(
      actorUserId: UUID,
      resourceAttributes: Map<String, String>,
    ): AccessConditionContext =
      AccessConditionContext(
        actorUserId = actorUserId,
        resourceAttributes = resourceAttributes,
      )
  }
}
