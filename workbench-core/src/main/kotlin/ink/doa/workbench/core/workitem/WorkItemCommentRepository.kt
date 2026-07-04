package ink.doa.workbench.core.workitem

import ink.doa.workbench.core.workitem.model.CreateWorkItemCommentCommand
import ink.doa.workbench.core.workitem.model.DeleteWorkItemCommentCommand
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommentCommand
import ink.doa.workbench.core.workitem.model.WorkItemCommentRecord
import java.util.UUID

interface WorkItemCommentRepository {
  suspend fun listByWorkItem(
    tenantId: UUID,
    issueId: UUID,
    limit: Int,
    offset: Long,
  ): List<WorkItemCommentRecord>

  suspend fun create(command: CreateWorkItemCommentCommand, issueId: UUID): WorkItemCommentRecord

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
