package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.workitem.model.CreateWorkItemCommentCommand
import ink.doa.workbench.agile.workitem.model.DeleteWorkItemCommentCommand
import ink.doa.workbench.agile.workitem.model.UpdateWorkItemCommentCommand
import ink.doa.workbench.agile.workitem.model.WorkItemCommentRecord
import ink.doa.workbench.agile.workitem.richtext.ProcessedRichText
import ink.doa.workbench.agile.workitem.richtext.RichTextDocument
import ink.doa.workbench.agile.workitem.richtext.RichTextProcessor
import ink.doa.workbench.identity.permission.model.AuthorizationAction
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.PermissionDeniedException
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class WorkItemCommentService(
  private val comments: WorkItemCommentRepository,
  private val accessPolicy: WorkItemAccessPolicyEngine,
  private val repository: ink.doa.workbench.agile.workitem.WorkItemRepository,
  private val configs: IssueTypeConfigRepository,
) {
  suspend fun create(command: CreateWorkItemCommentCommand): WorkItemCommentRecord {
    requirePermission(
      command.tenantId,
      command.projectId,
      command.authorId,
      command.workItemApiId,
      CREATE_ACTION,
    )
    val processed = processBody(command.body)
    val issueId = requireIssueId(command.tenantId, command.projectId, command.workItemApiId)
    return comments.create(command.withProcessedBody(processed), issueId).record
  }

  suspend fun update(command: UpdateWorkItemCommentCommand): WorkItemCommentRecord {
    requirePermission(
      command.tenantId,
      command.projectId,
      command.actorUserId,
      command.workItemApiId,
      UPDATE_ACTION,
    )
    val processed = processBody(command.body)
    val issueId = requireIssueId(command.tenantId, command.projectId, command.workItemApiId)
    return comments.update(command.withProcessedBody(processed), issueId)
  }

  suspend fun delete(command: DeleteWorkItemCommentCommand): WorkItemCommentRecord {
    requirePermission(
      command.tenantId,
      command.projectId,
      command.actorUserId,
      command.workItemApiId,
      DELETE_ACTION,
    )
    val issueId = requireIssueId(command.tenantId, command.projectId, command.workItemApiId)
    return comments.softDelete(command, issueId)
  }

  private suspend fun requireIssueId(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
  ): UUID =
    comments.resolveIssueId(tenantId, projectId, workItemApiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)

  private suspend fun requirePermission(
    tenantId: UUID,
    projectId: UUID,
    actorUserId: UUID,
    workItemApiId: String,
    action: AuthorizationAction,
  ) {
    if (
      !accessPolicy.bindingAllowsComment(
        tenantId = tenantId,
        projectId = projectId,
        actorUserId = actorUserId,
        action = action,
      )
    ) {
      throw PermissionDeniedException(permissionError(action))
    }
    val evaluation =
      WorkItemCommentPermissionSupport.evaluationContext(
        repository,
        configs,
        accessPolicy,
        CommentPermissionRequest(tenantId, projectId, actorUserId, workItemApiId),
      )
    if (!accessPolicy.isCommentPermitted(evaluation.issueTypeConfigId, evaluation.context)) {
      throw PermissionDeniedException(permissionError(action))
    }
  }

  private fun permissionError(action: AuthorizationAction): WorkbenchErrorCode =
    when (action.code) {
      CREATE_ACTION.code -> WorkbenchErrorCode.WORK_ITEM_COMMENT_CREATE_DENIED
      UPDATE_ACTION.code -> WorkbenchErrorCode.WORK_ITEM_COMMENT_UPDATE_DENIED
      else -> WorkbenchErrorCode.WORK_ITEM_COMMENT_DELETE_DENIED
    }

  private fun processBody(raw: RichTextDocument): ProcessedRichText {
    val processed = RichTextProcessor.process(raw)
    if (processed == null) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_COMMENT_BODY_REQUIRED)
    }
    if (processed.plainText.length > MAX_BODY_LENGTH) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_COMMENT_BODY_TOO_LONG)
    }
    return processed
  }

  private fun CreateWorkItemCommentCommand.withProcessedBody(
    processed: ProcessedRichText
  ): CreateWorkItemCommentCommand {
    return copy(
      body = processed.document,
      bodyPlainText = processed.plainText,
    )
  }

  private fun UpdateWorkItemCommentCommand.withProcessedBody(
    processed: ProcessedRichText
  ): UpdateWorkItemCommentCommand {
    return copy(
      body = processed.document,
      bodyPlainText = processed.plainText,
    )
  }

  private companion object {
    const val MAX_BODY_LENGTH = 32_768
    val CREATE_ACTION = AuthorizationAction("issue.comment.create")
    val UPDATE_ACTION = AuthorizationAction("issue.comment.update")
    val DELETE_ACTION = AuthorizationAction("issue.comment.delete")
  }
}
