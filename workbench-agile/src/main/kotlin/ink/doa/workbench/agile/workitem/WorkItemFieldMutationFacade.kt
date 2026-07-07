package ink.doa.workbench.agile.workitem

import org.springframework.stereotype.Component

@Component
class WorkItemFieldMutationFacade(
  val engine: WorkItemFieldMutationEngine,
  val descriptionAttachments: WorkItemDescriptionAttachmentValidator,
)
