package ink.doa.workbench.application.permission

import ink.doa.workbench.agile.workitem.access.AccessConditionContext
import ink.doa.workbench.agile.workitem.access.AccessConditionEvaluator
import ink.doa.workbench.identity.permission.PermissionConditionContext
import ink.doa.workbench.identity.permission.PermissionConditionResult

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
          actorUserApiId = context.actorUserApiId,
          resourceAttributes = context.resourceAttributes,
        ),
    )
}
