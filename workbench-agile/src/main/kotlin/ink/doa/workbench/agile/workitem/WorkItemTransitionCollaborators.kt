package ink.doa.workbench.agile.workitem

import org.springframework.stereotype.Component

@Component
class WorkItemTransitionCollaborators(
  val mutationSupport: WorkItemMutationSupport,
  val activityEnqueueSupport: WorkItemActivityEnqueueSupport,
  val fieldMutationEngine: WorkItemFieldMutationEngine,
  val commentService: WorkItemCommentService,
  val transitionValidator: WorkItemTransitionValidator,
  val transitionOptions: WorkItemTransitionOptionBuilder,
)
