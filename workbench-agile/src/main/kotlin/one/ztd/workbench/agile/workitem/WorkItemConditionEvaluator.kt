package one.ztd.workbench.agile.workitem

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import one.ztd.workbench.agile.workitem.access.AccessConditionContext
import one.ztd.workbench.agile.workitem.access.AccessConditionEvaluator
import one.ztd.workbench.agile.workitem.model.WorkItemRecord
import one.ztd.workbench.agile.workitem.query.WorkItemConditionJson

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
