package one.ztd.workbench.agile.workitem

import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import one.ztd.workbench.agile.workitem.access.AccessConditionContext
import one.ztd.workbench.agile.workitem.access.AccessConditionEvaluator
import one.ztd.workbench.agile.workitem.template.TemplateField
import one.ztd.workbench.agile.workitem.template.toPermissionResourceId
import one.ztd.workbench.identity.permission.PermissionBindingRepository
import one.ztd.workbench.identity.permission.PermissionConditionResult
import one.ztd.workbench.identity.permission.ResolvedPermissionRule
import one.ztd.workbench.identity.permission.model.AuthorizationAction
import one.ztd.workbench.identity.permission.model.PermissionEffect
import org.springframework.stereotype.Component

@Component
class WorkItemBindingPermissionEvaluator(
  private val bindings: PermissionBindingRepository,
  private val conditionEvaluator: AccessConditionEvaluator,
  private val clock: Clock,
) {
  suspend fun allowsFieldWrite(
    context: WorkItemFieldPermissionContext,
    field: TemplateField,
    resourceAttributes: Map<String, String> = emptyMap(),
  ): Boolean {
    val rules = activeRules(context.tenantId, context.projectId, context.actorUserId)
    val fieldResource = field.toPermissionResourceId()
    val fieldRules = rules.filter { it.matchesFieldWrite(fieldResource) }
    val conditionContext =
      AccessConditionContext.fromResourceAttributes(
        actorUserApiId = context.actorUserApiId,
        resourceAttributes = resourceAttributes,
      )
    if (fieldRules.any { it.denies(conditionContext) }) return false
    if (fieldRules.any { it.allows(conditionContext) }) return true
    return when (context.operation) {
      FieldPermissionOperation.CREATE -> true
      FieldPermissionOperation.UPDATE ->
        rules.any { it.matchesIssueUpdate() && it.allows(conditionContext) }
    }
  }

  suspend fun allowsComment(
    tenantId: java.util.UUID,
    projectId: java.util.UUID,
    actorUserId: java.util.UUID,
    action: AuthorizationAction,
  ): Boolean {
    val rules = activeRules(tenantId, projectId, actorUserId)
    return rules.any {
      it.action == action &&
        it.effect == PermissionEffect.ALLOW &&
        (it.resourcePattern == "issue:*" || it.resourcePattern == "*")
    }
  }

  private suspend fun activeRules(
    tenantId: java.util.UUID,
    projectId: java.util.UUID,
    actorUserId: java.util.UUID,
  ): List<ResolvedPermissionRule> =
    bindings.listActiveRulesForSubject(
      subjectUserId = actorUserId,
      tenantId = tenantId,
      projectId = projectId,
      at = now(),
    )

  private fun ResolvedPermissionRule.denies(context: AccessConditionContext): Boolean =
    effect == PermissionEffect.DENY &&
      conditionEvaluator.evaluateJsonString(conditionJson, context).let {
        it == PermissionConditionResult.MATCH || it == PermissionConditionResult.INVALID
      }

  private fun ResolvedPermissionRule.allows(context: AccessConditionContext): Boolean =
    effect == PermissionEffect.ALLOW &&
      conditionEvaluator.evaluateJsonString(conditionJson, context) ==
        PermissionConditionResult.MATCH

  private fun ResolvedPermissionRule.matchesFieldWrite(fieldResource: String): Boolean =
    action == FIELD_WRITE_ACTION &&
      (resourcePattern == "issue:field:*" || resourcePattern == fieldResource)

  private fun ResolvedPermissionRule.matchesIssueUpdate(): Boolean =
    action == ISSUE_UPDATE_ACTION && (resourcePattern == "issue:*" || resourcePattern == "*")

  private fun now(): OffsetDateTime = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)

  private companion object {
    val FIELD_WRITE_ACTION = AuthorizationAction("issue.field.write")
    val ISSUE_UPDATE_ACTION = AuthorizationAction("issue.update")
  }
}
