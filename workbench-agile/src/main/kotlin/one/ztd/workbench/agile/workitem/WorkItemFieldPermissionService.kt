package one.ztd.workbench.agile.workitem

import one.ztd.workbench.agile.workitem.template.FieldParticipation
import one.ztd.workbench.agile.workitem.template.FieldWriteGrant
import one.ztd.workbench.agile.workitem.template.TemplateField
import one.ztd.workbench.agile.workitem.template.TransitionFieldSpec
import org.springframework.stereotype.Service

@Service
class WorkItemFieldPermissionService(private val accessPolicy: WorkItemAccessPolicyEngine) {
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
    val bindingAllowed =
      accessPolicy.bindingAllowsFieldWrite(
        context = context,
        field = field,
        resourceAttributes = context.resourceAttributes,
      )
    if (!bindingAllowed) return false
    val evaluation = context.accessEvaluation ?: return bindingAllowed
    return accessPolicy.isFieldWritePermitted(
      issueTypeConfigId = evaluation.issueTypeConfigId,
      field = field,
      evaluationContext = evaluation,
      preloadedRules = context.accessRules,
    )
  }
}
