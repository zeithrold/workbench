package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommentCommand
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import org.springframework.stereotype.Component

@Component
class WorkItemTransitionExecutor(
  private val repository: WorkItemRepository,
  private val mutationSupport: WorkItemMutationSupport,
  private val commentService: WorkItemCommentService,
) {
  suspend fun execute(command: TransitionExecutionCommand): WorkItemMutationResult {
    val result =
      repository
        .transition(
          command = command.persistence,
          fromStatusId = command.issueStatusId,
          toStatusId = command.transition.toStatusId,
          transitionId = command.transition.id,
          propertyValues = command.propertyValues,
        )
        .also { mutationSupport.publish(it) }
    if (command.commentBody != null) {
      commentService.create(
        CreateWorkItemCommentCommand(
          tenantId = command.request.tenantId,
          projectId = command.request.projectId,
          workItemApiId = command.request.workItemApiId,
          authorId = command.request.actorUserId,
          body = command.commentBody,
          transitionId = command.transition.id,
          statusHistoryId = result.statusHistoryId,
        )
      )
    }
    return result
  }
}
