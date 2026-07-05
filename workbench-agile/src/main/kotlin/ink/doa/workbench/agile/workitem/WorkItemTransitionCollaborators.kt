package ink.doa.workbench.agile.workitem

import org.springframework.stereotype.Component

@Component
class WorkItemTransitionCollaborators(
  val mutationSupport: WorkItemMutationSupport,
  val activityEnqueueSupport: WorkItemActivityEnqueueSupport,
  val fieldMutationReconciler: WorkItemFieldMutationReconciler,
  val commentService: WorkItemCommentService,
  val transitionValidator: WorkItemTransitionValidator,
  val transitionOptions: WorkItemTransitionOptionBuilder,
)
