package one.ztd.workbench.agile.workitem

import java.util.UUID
import one.ztd.workbench.agile.workitem.model.TransitionPersistenceCommand
import one.ztd.workbench.agile.workitem.model.TransitionRequest
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyValue
import one.ztd.workbench.agile.workitem.model.WorkflowTransitionRecord
import one.ztd.workbench.agile.workitem.richtext.RichTextDocument

data class TransitionExecutionCommand(
  val request: TransitionRequest,
  val issueStatusId: UUID,
  val transition: WorkflowTransitionRecord,
  val persistence: TransitionPersistenceCommand,
  val propertyValues: List<WorkItemPropertyValue>,
  val commentBody: RichTextDocument?,
)
