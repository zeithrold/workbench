package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.PermissionDeniedException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.permission.PermissionBindingRepository
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.core.workitem.WorkItemCommentRepository
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommentCommand
import ink.doa.workbench.core.workitem.model.DeleteWorkItemCommentCommand
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommentCommand
import ink.doa.workbench.core.workitem.model.WorkItemCommentRecord
import ink.doa.workbench.core.workitem.richtext.ProcessedRichText
import ink.doa.workbench.core.workitem.richtext.RichTextProcessor
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class WorkItemCommentService(
  private val comments: WorkItemCommentRepository,
  private val bindings: PermissionBindingRepository,
  private val clock: Clock,
) {
  suspend fun list(
    tenantId: UUID,
    projectId: UUID,
    workItemApiId: String,
    limit: Int = 50,
    offset: Long = 0,
  ): List<WorkItemCommentRecord> {
    val issueId = requireIssueId(tenantId, projectId, workItemApiId)
    return comments.listByWorkItem(tenantId, issueId, limit, offset)
  }

  suspend fun create(command: CreateWorkItemCommentCommand): WorkItemCommentRecord {
    requirePermission(command.tenantId, command.projectId, command.authorId, CREATE_ACTION)
    val processed = processBody(command.body)
    val issueId = requireIssueId(command.tenantId, command.projectId, command.workItemApiId)
    return comments.create(command.withProcessedBody(processed), issueId)
  }

  suspend fun update(command: UpdateWorkItemCommentCommand): WorkItemCommentRecord {
    requirePermission(command.tenantId, command.projectId, command.actorUserId, UPDATE_ACTION)
    val processed = processBody(command.body)
    val issueId = requireIssueId(command.tenantId, command.projectId, command.workItemApiId)
    return comments.update(command.withProcessedBody(processed), issueId)
  }

  suspend fun delete(command: DeleteWorkItemCommentCommand): WorkItemCommentRecord {
    requirePermission(command.tenantId, command.projectId, command.actorUserId, DELETE_ACTION)
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
    action: AuthorizationAction,
  ) {
    val rules =
      bindings.listActiveRulesForSubject(
        subjectUserId = actorUserId,
        tenantId = tenantId,
        projectId = projectId,
        at = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
      )
    val allowed = rules.any {
      it.action == action &&
        (it.effect == PermissionEffect.ALLOW) &&
        (it.resourcePattern == "issue:*" || it.resourcePattern == "*")
    }
    if (!allowed) {
      throw PermissionDeniedException(permissionError(action))
    }
  }

  private fun permissionError(action: AuthorizationAction): WorkbenchErrorCode =
    when (action.code) {
      CREATE_ACTION.code -> WorkbenchErrorCode.WORK_ITEM_COMMENT_CREATE_DENIED
      UPDATE_ACTION.code -> WorkbenchErrorCode.WORK_ITEM_COMMENT_UPDATE_DENIED
      else -> WorkbenchErrorCode.WORK_ITEM_COMMENT_DELETE_DENIED
    }

  private fun processBody(raw: String): ProcessedRichText {
    val processed = RichTextProcessor.processCommentInput(raw)
    val bodyRequired = raw.isBlank() || processed == null || processed.html.isNullOrBlank()
    if (bodyRequired) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_COMMENT_BODY_REQUIRED)
    }
    if ((processed.plainText?.length ?: 0) > MAX_BODY_LENGTH) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_COMMENT_BODY_TOO_LONG)
    }
    return processed
  }

  private fun CreateWorkItemCommentCommand.withProcessedBody(
    processed: ProcessedRichText
  ): CreateWorkItemCommentCommand {
    val html =
      requireNotNull(processed.html) {
        "Processed comment body must include HTML content"
      }
    return copy(
      body = html,
      bodyPlainText = processed.plainText,
      bodyFormat = HTML_FORMAT,
    )
  }

  private fun UpdateWorkItemCommentCommand.withProcessedBody(
    processed: ProcessedRichText
  ): UpdateWorkItemCommentCommand {
    val html =
      requireNotNull(processed.html) {
        "Processed comment body must include HTML content"
      }
    return copy(
      body = html,
      bodyPlainText = processed.plainText,
    )
  }

  private companion object {
    const val MAX_BODY_LENGTH = 32_768
    const val HTML_FORMAT = CreateWorkItemCommentCommand.HTML_FORMAT
    val CREATE_ACTION = AuthorizationAction("issue.comment.create")
    val UPDATE_ACTION = AuthorizationAction("issue.comment.update")
    val DELETE_ACTION = AuthorizationAction("issue.comment.delete")
  }
}
