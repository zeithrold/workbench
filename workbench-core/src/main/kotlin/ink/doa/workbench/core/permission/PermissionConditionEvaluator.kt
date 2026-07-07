package ink.doa.workbench.core.permission

import ink.doa.workbench.core.workitem.access.AccessConditionContext
import ink.doa.workbench.core.workitem.access.AccessConditionEvaluator

class PermissionConditionEvaluator(
  private val delegate: AccessConditionEvaluator = AccessConditionEvaluator()
) {
  fun evaluate(
    conditionJson: String?,
    context: PermissionConditionContext,
  ): PermissionConditionResult =
    delegate.evaluateJsonString(
      conditionJson = conditionJson,
      context =
        AccessConditionContext.fromResourceAttributes(
          actorUserId = context.actorUserId,
          resourceAttributes = context.resourceAttributes,
        ),
    )
}
