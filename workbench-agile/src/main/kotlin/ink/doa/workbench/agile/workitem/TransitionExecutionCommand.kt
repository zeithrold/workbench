package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.workitem.model.TransitionPersistenceCommand
import ink.doa.workbench.agile.workitem.model.TransitionRequest
import ink.doa.workbench.agile.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.agile.workitem.model.WorkflowTransitionRecord
import ink.doa.workbench.agile.workitem.richtext.RichTextDocument
import java.util.UUID

data class TransitionExecutionCommand(
  val request: TransitionRequest,
  val issueStatusId: UUID,
  val transition: WorkflowTransitionRecord,
  val persistence: TransitionPersistenceCommand,
  val propertyValues: List<WorkItemPropertyValue>,
  val commentBody: RichTextDocument?,
)
