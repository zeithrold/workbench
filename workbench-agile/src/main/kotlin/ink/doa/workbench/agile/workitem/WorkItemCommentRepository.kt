package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.workitem.model.CreateWorkItemCommentCommand
import ink.doa.workbench.agile.workitem.model.DeleteWorkItemCommentCommand
import ink.doa.workbench.agile.workitem.model.UpdateWorkItemCommentCommand
import ink.doa.workbench.agile.workitem.model.WorkItemCommentCreateResult
import ink.doa.workbench.agile.workitem.model.WorkItemCommentRecord
import java.util.UUID

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
