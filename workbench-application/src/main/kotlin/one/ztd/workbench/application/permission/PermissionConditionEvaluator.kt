package one.ztd.workbench.application.permission

import one.ztd.workbench.agile.workitem.access.AccessConditionContext
import one.ztd.workbench.agile.workitem.access.AccessConditionEvaluator
import one.ztd.workbench.identity.permission.PermissionConditionContext
import one.ztd.workbench.identity.permission.PermissionConditionResult

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
