package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.template.WorkItemTransitionFieldsTemplate
import ink.doa.workbench.core.workitem.template.WorkItemValueTemplateContext
import ink.doa.workbench.core.workitem.template.WorkItemValueTemplateTarget
import org.springframework.stereotype.Component

@Component
class WorkItemFieldMutationSupport(
  val reconciler: WorkItemFieldMutationReconciler,
  val permissions: WorkItemFieldPermissionService,
  val descriptionAttachments: WorkItemDescriptionAttachmentValidator,
) {
  suspend fun reconcileCreate(
    command: CreateWorkItemCommand,
    config: IssueTypeConfigDetails,
    fieldsTemplate: WorkItemTransitionFieldsTemplate,
    templateContext: WorkItemValueTemplateContext,
    permissionContext: WorkItemFieldPermissionContext,
  ): TransitionFieldReconcileResult =
    reconciler.reconcileFields(
      FieldReconciliationContext(
        template = fieldsTemplate,
        expectedTarget = WorkItemValueTemplateTarget.CREATE,
        config = config,
        templateContext = templateContext,
        currentProperties = emptyMap(),
        userProperties = WorkItemPropertySupport.createFieldInputs(command),
        permissionContext = permissionContext.copy(operation = FieldPermissionOperation.CREATE),
      )
    )
}
