package one.ztd.workbench.agile.workitem

import one.ztd.workbench.agile.workitem.model.CreateWorkItemCommentCommand
import one.ztd.workbench.agile.workitem.model.WorkItemSearchHit
import org.springframework.stereotype.Component

@Component
class WorkItemTransitionExecutor(
  private val repository: WorkItemRepository,
  private val commentService: WorkItemCommentService,
  private val readModels: WorkItemReadModelService,
) {
  suspend fun execute(command: TransitionExecutionCommand): WorkItemSearchHit {
    val result =
      repository.transition(
        command = command.persistence,
        fromStatusId = command.issueStatusId,
        toStatusId = command.transition.toStatusId,
        transitionId = command.transition.id,
        propertyValues = command.propertyValues,
      )
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
    return readModels.afterMutation(result)
  }
}
