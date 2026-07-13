package ink.doa.workbench.data.support

import ink.doa.workbench.agile.workitem.model.IssueStatusRecord
import ink.doa.workbench.agile.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.agile.workitem.model.IssueTypeRecord
import ink.doa.workbench.agile.workitem.model.WorkflowRecord
import java.util.UUID

internal data class WorkItemStack(
  val tenantId: UUID,
  val actorId: UUID,
  val projectId: UUID,
  val issueType: IssueTypeRecord,
  val workflow: WorkflowRecord,
  val todoStatus: IssueStatusRecord,
  val doneStatus: IssueStatusRecord,
  val config: IssueTypeConfigDetails,
)
