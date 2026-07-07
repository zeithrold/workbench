package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.workitem.template.FieldParticipation
import ink.doa.workbench.core.workitem.template.TransitionFieldSpec

/**
 * Unified permission decision for a single field in a work-item mutation.
 *
 * [allowsUserSubmission] governs request hygiene (may the client include this key?).
 * [bindingAllowsWrite] governs inherit-grant value selection during reconciliation.
 */
data class FieldMutationPolicy(
  val allowsUserSubmission: Boolean,
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
)

fun FieldMutationPolicy.allowsFormEdit(spec: TransitionFieldSpec): Boolean =
  spec.participation != FieldParticipation.AUTOMATIC && allowsUserSubmission
