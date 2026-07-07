package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.core.workitem.access.AccessConditionContext
import ink.doa.workbench.core.workitem.access.AccessConditionEvaluator
import ink.doa.workbench.core.workitem.access.WorkItemAccessActionType
import ink.doa.workbench.core.workitem.access.WorkItemAccessActor
import ink.doa.workbench.core.workitem.access.WorkItemAccessEvaluationContext
import ink.doa.workbench.core.workitem.access.WorkItemAccessRuleRecord
import ink.doa.workbench.core.workitem.access.WorkItemAccessRuleRepository
import ink.doa.workbench.core.workitem.access.WorkItemAccessSubjectType
import ink.doa.workbench.core.workitem.template.TemplateField
import ink.doa.workbench.core.workitem.template.toPermissionResourceId
import java.util.UUID
import org.springframework.stereotype.Component

private data class TypeScopedActionQuery(
  val issueTypeConfigId: UUID,
  val evaluationContext: WorkItemAccessEvaluationContext,
  val actionType: WorkItemAccessActionType,
  val transitionId: UUID?,
  val fieldKey: String?,
  val defaultWhenNoRules: Boolean,
)

@Component
class WorkItemAccessPolicyEngine(
  private val accessRules: WorkItemAccessRuleRepository,
  private val principalResolver: WorkItemAccessPrincipalResolver,
  private val bindingEvaluator: WorkItemBindingPermissionEvaluator,
  private val conditionEvaluator: AccessConditionEvaluator,
) {
  suspend fun resolveActor(
    tenantId: UUID,
    projectId: UUID,
    actorUserId: UUID,
  ): WorkItemAccessActor = principalResolver.resolveActor(tenantId, projectId, actorUserId)

  suspend fun isTransitionPermitted(
    issueTypeConfigId: UUID,
    transitionId: UUID,
    evaluationContext: WorkItemAccessEvaluationContext,
  ): Boolean =
    evaluateTypeScopedAction(
      TypeScopedActionQuery(
        issueTypeConfigId = issueTypeConfigId,
        evaluationContext = evaluationContext,
        actionType = WorkItemAccessActionType.TRANSITION,
        transitionId = transitionId,
        fieldKey = null,
        defaultWhenNoRules = true,
      )
    )

  suspend fun isFieldWritePermitted(
    issueTypeConfigId: UUID,
    field: TemplateField,
    evaluationContext: WorkItemAccessEvaluationContext,
  ): Boolean {
    val fieldKey = field.toPermissionResourceId().removePrefix("issue:field:")
    if (
      !evaluateTypeScopedAction(
        TypeScopedActionQuery(
          issueTypeConfigId = issueTypeConfigId,
          evaluationContext = evaluationContext,
          actionType = WorkItemAccessActionType.FIELD_WRITE_ALL,
          transitionId = null,
          fieldKey = null,
          defaultWhenNoRules = true,
        )
      )
    ) {
      return false
    }
    return evaluateTypeScopedAction(
      TypeScopedActionQuery(
        issueTypeConfigId = issueTypeConfigId,
        evaluationContext = evaluationContext,
        actionType = WorkItemAccessActionType.FIELD_WRITE,
        transitionId = null,
        fieldKey = fieldKey,
        defaultWhenNoRules = true,
      )
    )
  }

  suspend fun isCommentPermitted(
    issueTypeConfigId: UUID,
    evaluationContext: WorkItemAccessEvaluationContext,
  ): Boolean =
    evaluateTypeScopedAction(
      TypeScopedActionQuery(
        issueTypeConfigId = issueTypeConfigId,
        evaluationContext = evaluationContext,
        actionType = WorkItemAccessActionType.COMMENT,
        transitionId = null,
        fieldKey = null,
        defaultWhenNoRules = true,
      )
    )

  suspend fun bindingAllowsFieldWrite(
    context: WorkItemFieldPermissionContext,
    field: TemplateField,
    resourceAttributes: Map<String, String> = emptyMap(),
  ): Boolean = bindingEvaluator.allowsFieldWrite(context, field, resourceAttributes)

  suspend fun bindingAllowsComment(
    tenantId: UUID,
    projectId: UUID,
    actorUserId: UUID,
    action: ink.doa.workbench.core.permission.model.AuthorizationAction,
  ): Boolean = bindingEvaluator.allowsComment(tenantId, projectId, actorUserId, action)

  private suspend fun evaluateTypeScopedAction(query: TypeScopedActionQuery): Boolean {
    val rules =
      accessRules
        .listByConfig(query.evaluationContext.workItem.tenantId, query.issueTypeConfigId)
        .filter { rule ->
          rule.actionType == query.actionType &&
            rule.matchesTarget(query.transitionId, query.fieldKey)
        }
    if (rules.isEmpty()) return query.defaultWhenNoRules
    val conditionContext =
      AccessConditionContext.fromEvaluation(
        query.evaluationContext,
        query.evaluationContext.projectApiId,
      )
    val matching = rules.filter { rule ->
      matchesSubject(rule, query.evaluationContext.actor) &&
        conditionEvaluator.evaluateObject(rule.condition, conditionContext)
    }
    return when {
      matching.any { it.effect == PermissionEffect.DENY } -> false
      matching.any { it.effect == PermissionEffect.ALLOW } -> true
      rules.any { it.effect == PermissionEffect.ALLOW } -> false
      else -> query.defaultWhenNoRules
    }
  }

  private fun matchesSubject(rule: WorkItemAccessRuleRecord, actor: WorkItemAccessActor): Boolean =
    when (rule.subjectType) {
      WorkItemAccessSubjectType.ANYONE -> true
      WorkItemAccessSubjectType.USER -> rule.subjectUserId == actor.userId
      WorkItemAccessSubjectType.IN_GROUP -> rule.subjectGroupId in actor.groupIds
      WorkItemAccessSubjectType.NOT_IN_GROUP -> rule.subjectGroupId !in actor.groupIds
      WorkItemAccessSubjectType.IN_ROLE -> rule.subjectRoleCode in actor.projectRoles
      WorkItemAccessSubjectType.NOT_IN_ROLE -> rule.subjectRoleCode !in actor.projectRoles
    }

  private fun WorkItemAccessRuleRecord.matchesTarget(
    transitionId: UUID?,
    fieldKey: String?,
  ): Boolean =
    when (actionType) {
      WorkItemAccessActionType.TRANSITION -> this.transitionId == transitionId
      WorkItemAccessActionType.FIELD_WRITE ->
        fieldKey != null && (this.fieldKey == "*" || this.fieldKey == fieldKey)
      WorkItemAccessActionType.FIELD_WRITE_ALL -> true
      WorkItemAccessActionType.COMMENT -> true
    }
}
