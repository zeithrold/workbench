package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.workitem.model.TransitionPersistenceCommand
import ink.doa.workbench.core.workitem.model.TransitionRequest
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
import java.util.UUID

data class TransitionExecutionCommand(
  val request: TransitionRequest,
  val issueStatusId: UUID,
  val transition: WorkflowTransitionRecord,
  val persistence: TransitionPersistenceCommand,
  val propertyValues: List<WorkItemPropertyValue>,
  val commentBody: String?,
)
