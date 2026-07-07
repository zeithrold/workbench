package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.permission.PermissionBindingRepository
import ink.doa.workbench.core.permission.ResolvedPermissionRule
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.core.workitem.template.FieldParticipation
import ink.doa.workbench.core.workitem.template.FieldWriteGrant
import ink.doa.workbench.core.workitem.template.TemplateField
import ink.doa.workbench.core.workitem.template.TransitionFieldSpec
import ink.doa.workbench.core.workitem.template.toPermissionResourceId
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@Service
class WorkItemFieldPermissionService(
  private val bindings: PermissionBindingRepository,
  private val clock: Clock,
) {
  suspend fun resolvePolicy(
    context: WorkItemFieldPermissionContext,
    field: TemplateField,
    spec: TransitionFieldSpec,
  ): FieldMutationPolicy {
    val bindingAllowsWrite = bindingAllowsWrite(context, field)
    val submission =
      when {
        spec.participation == FieldParticipation.AUTOMATIC -> FieldSubmissionPolicy.READ_ONLY
        spec.writeGrant == FieldWriteGrant.IMMUTABLE -> FieldSubmissionPolicy.READ_ONLY
        spec.writeGrant == FieldWriteGrant.SYSTEM_ONLY -> FieldSubmissionPolicy.READ_ONLY
        spec.writeGrant == FieldWriteGrant.TRANSITION_WRITABLE ->
          FieldSubmissionPolicy.TRANSITION_OVERRIDE
        else -> FieldSubmissionPolicy.INHERIT_BINDING
      }
    return FieldMutationPolicy(submission, bindingAllowsWrite)
  }

  suspend fun resolvePatchPolicy(
    context: WorkItemFieldPermissionContext,
    field: TemplateField,
  ): FieldMutationPolicy {
    val bindingAllowsWrite = bindingAllowsWrite(context, field)
    val submission =
      if (bindingAllowsWrite) {
        FieldSubmissionPolicy.INHERIT_BINDING
      } else {
        FieldSubmissionPolicy.READ_ONLY
      }
    return FieldMutationPolicy(submission, bindingAllowsWrite)
  }

  suspend fun bindingAllowsWrite(
    context: WorkItemFieldPermissionContext,
    field: TemplateField,
  ): Boolean {
    val rules =
      bindings.listActiveRulesForSubject(
        subjectUserId = context.actorUserId,
        tenantId = context.tenantId,
        projectId = context.projectId,
        at = now(),
      )
    val fieldResource = field.toPermissionResourceId()
    val fieldRules = rules.filter { it.matchesFieldWrite(fieldResource) }
    if (fieldRules.any { it.effect == PermissionEffect.DENY }) return false
    if (fieldRules.any { it.effect == PermissionEffect.ALLOW }) return true
    return when (context.operation) {
      FieldPermissionOperation.CREATE -> true
      FieldPermissionOperation.UPDATE -> rules.any { it.matchesIssueUpdate() }
    }
  }

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
