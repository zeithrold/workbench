package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.workitem.access.AccessConditionContext
import ink.doa.workbench.core.workitem.access.AccessConditionEvaluator
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.query.WorkItemConditionJson
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class WorkItemConditionContext(
  val workItem: WorkItemRecord,
  val actorUserId: UUID,
  val properties: Map<String, JsonElement>,
  val childIssuesNotDone: Long = 0,
)

class WorkItemConditionEvaluator(
  private val delegate: AccessConditionEvaluator = AccessConditionEvaluator()
) {
  fun evaluate(ast: JsonObject, context: WorkItemConditionContext): Boolean =
    delegate.evaluateObject(
      condition = ast,
      context =
        AccessConditionContext(
          actorUserId = context.actorUserId,
          workItem = context.workItem,
          properties = context.properties,
          childIssuesNotDone = context.childIssuesNotDone,
        ),
    )

  fun isEmpty(ast: JsonObject): Boolean = WorkItemConditionJson.parse(ast) == null
}
