package one.ztd.workbench.agile.workitem.access

import java.util.UUID
import kotlinx.serialization.json.JsonElement
import one.ztd.workbench.agile.workitem.model.WorkItemRecord

data class AccessConditionContext(
  val actorUserApiId: String,
  val actorGroupIds: Set<UUID> = emptySet(),
  val workItem: WorkItemRecord? = null,
  val projectApiId: String? = null,
  val properties: Map<String, JsonElement> = emptyMap(),
  val childIssuesNotDone: Long = 0,
  val resourceAttributes: Map<String, String> = emptyMap(),
) {
  companion object {
    fun fromEvaluation(
      context: WorkItemAccessEvaluationContext,
      projectApiId: String?,
    ): AccessConditionContext =
      AccessConditionContext(
        actorUserApiId = context.actor.userApiId,
        actorGroupIds = context.actor.groupIds,
        workItem = context.workItem,
        projectApiId = projectApiId,
        properties = context.properties,
        childIssuesNotDone = context.childIssuesNotDone,
      )

    fun fromResourceAttributes(
      actorUserApiId: String,
      resourceAttributes: Map<String, String>,
    ): AccessConditionContext =
      AccessConditionContext(
        actorUserApiId = actorUserApiId,
        resourceAttributes = resourceAttributes,
      )
  }
}
