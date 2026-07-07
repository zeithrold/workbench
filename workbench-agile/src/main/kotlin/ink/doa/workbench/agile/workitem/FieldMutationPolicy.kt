package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.workitem.template.FieldParticipation
import ink.doa.workbench.core.workitem.template.TransitionFieldSpec

enum class FieldSubmissionPolicy {
  /**
   * Inherit field-write binding; unauthorized submissions follow
   * [TransitionFieldSpec.onUnauthorized].
   */
  INHERIT_BINDING,
  /** Allow user submission regardless of field-write binding. */
  TRANSITION_OVERRIDE,
  /** User may not submit; reconciliation applies template or current values. */
  READ_ONLY,
}

/**
 * Unified permission decision for a single field in a work-item mutation.
 *
 * [submission] governs whether the client may include the field in the request.
 * [bindingAllowsWrite] governs inherit-grant value selection during reconciliation.
 */
data class FieldMutationPolicy(
  val submission: FieldSubmissionPolicy,
  val bindingAllowsWrite: Boolean,
)

enum class FieldPermissionOperation {
  CREATE,
  UPDATE,
}

data class WorkItemFieldPermissionContext(
  val tenantId: java.util.UUID,
  val projectId: java.util.UUID,
  val actorUserId: java.util.UUID,
  val operation: FieldPermissionOperation,
  val accessEvaluation: ink.doa.workbench.core.workitem.access.WorkItemAccessEvaluationContext? =
    null,
  val resourceAttributes: Map<String, String> = emptyMap(),
)

fun FieldMutationPolicy.allowsFormEdit(spec: TransitionFieldSpec): Boolean =
  spec.participation != FieldParticipation.AUTOMATIC &&
    when (submission) {
      FieldSubmissionPolicy.TRANSITION_OVERRIDE -> true
      FieldSubmissionPolicy.INHERIT_BINDING -> bindingAllowsWrite
      FieldSubmissionPolicy.READ_ONLY -> false
    }

fun FieldMutationPolicy.allowsPatchSubmission(): Boolean =
  when (submission) {
    FieldSubmissionPolicy.TRANSITION_OVERRIDE -> true
    FieldSubmissionPolicy.INHERIT_BINDING -> bindingAllowsWrite
    FieldSubmissionPolicy.READ_ONLY -> false
  }
