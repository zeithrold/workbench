package one.ztd.workbench.agile.workitem

import java.util.UUID
import one.ztd.workbench.agile.workitem.model.TransitionRequest
import one.ztd.workbench.agile.workitem.model.WorkItemMutationResult
import one.ztd.workbench.agile.workitem.model.WorkItemTransitionOption
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import org.springframework.stereotype.Service

@Service
class WorkItemTransitionService(
  private val workflows: WorkflowConfigurationRepository,
  private val contextLoader: WorkItemTransitionContextLoader,
  private val evaluator: WorkItemTransitionEvaluator,
  private val fieldPipeline: WorkItemFieldMutationPipeline,
  private val optionBuilder: WorkItemTransitionOptionBuilder,
  private val executor: WorkItemTransitionExecutor,
) {
  suspend fun availableTransitions(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
    actorUserId: UUID,
    actorUserApiId: String,
  ): List<WorkItemTransitionOption> {
    val context =
      contextLoader.loadForIssue(
        tenantId,
        projectId,
        workItemApiId,
        actorUserId,
        actorUserApiId,
      )
    return workflows
      .listTransitions(tenantId, context.config.config.workflowId)
      .filter { it.fromStatusId == null || it.fromStatusId == context.issue.statusId }
      .map { transition ->
        val evaluation = evaluator.evaluate(context, transition)
        optionBuilder.build(transition, context, evaluation)
      }
  }

  suspend fun transition(request: TransitionRequest): WorkItemMutationResult {
    val context = contextLoader.load(request)
    val transition =
      workflows.findTransition(request.tenantId, request.transitionApiId)
        ?: throw ResourceNotFoundException(
          WorkbenchErrorCode.RESOURCE_WORKFLOW_TRANSITION_NOT_FOUND
        )
    val evaluation = evaluator.evaluateOrThrow(context, transition)
    val fieldsTemplate =
      evaluation.fieldsTemplate
        ?: error("Transition fields template must be present after evaluation")
    val plan = fieldPipeline.applyTransition(request, context, fieldsTemplate)
    return executor.execute(
      TransitionExecutionCommand(
        request = request,
        issueStatusId = context.issue.statusId,
        transition = transition,
        persistence = plan.persistence,
        propertyValues = plan.propertyValues,
        commentBody = plan.commentBody,
      )
    )
  }
}
