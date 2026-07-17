package one.ztd.workbench.agile.workitem

import java.util.UUID
import one.ztd.workbench.agile.workitem.model.CreateWorkItemCommentCommand
import one.ztd.workbench.agile.workitem.model.DeleteWorkItemCommentCommand
import one.ztd.workbench.agile.workitem.model.UpdateWorkItemCommentCommand
import one.ztd.workbench.agile.workitem.model.WorkItemCommentCreateResult
import one.ztd.workbench.agile.workitem.model.WorkItemCommentRecord

interface WorkItemCommentRepository {
  suspend fun create(
    command: CreateWorkItemCommentCommand,
    issueId: UUID,
  ): WorkItemCommentCreateResult

  suspend fun findByApiId(
    tenantId: UUID,
    issueId: UUID,
    commentApiId: String,
  ): WorkItemCommentRecord?

  suspend fun update(command: UpdateWorkItemCommentCommand, issueId: UUID): WorkItemCommentRecord

  suspend fun softDelete(
    command: DeleteWorkItemCommentCommand,
    issueId: UUID,
  ): WorkItemCommentRecord

  suspend fun resolveIssueId(tenantId: UUID, projectId: UUID, workItemApiId: String): UUID?
}
