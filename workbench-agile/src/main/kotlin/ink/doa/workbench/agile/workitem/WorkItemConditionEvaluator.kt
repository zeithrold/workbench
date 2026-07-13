package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.workitem.access.AccessConditionContext
import ink.doa.workbench.agile.workitem.access.AccessConditionEvaluator
import ink.doa.workbench.agile.workitem.model.WorkItemRecord
import ink.doa.workbench.agile.workitem.query.WorkItemConditionJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class WorkItemConditionContext(
  val workItem: WorkItemRecord,
  val actorUserApiId: String,
  val projectApiId: String,
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
          actorUserApiId = context.actorUserApiId,
          workItem = context.workItem,
          projectApiId = context.projectApiId,
          properties = context.properties,
          childIssuesNotDone = context.childIssuesNotDone,
        ),
    )

  fun isEmpty(ast: JsonObject): Boolean = WorkItemConditionJson.parse(ast) == null
}
