package one.ztd.workbench.data.support

import java.util.UUID
import one.ztd.workbench.agile.workitem.model.IssueStatusRecord
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigDetails
import one.ztd.workbench.agile.workitem.model.IssueTypeRecord
import one.ztd.workbench.agile.workitem.model.WorkflowRecord

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
