package ink.doa.workbench.agile.testfixtures

import ink.doa.workbench.agile.workitem.WorkItemActivityEnqueueSupport
import ink.doa.workbench.agile.workitem.WorkItemCommentService
import ink.doa.workbench.agile.workitem.WorkItemDescriptionAttachmentValidator
import ink.doa.workbench.agile.workitem.WorkItemFieldMutationReconciler
import ink.doa.workbench.agile.workitem.WorkItemFieldMutationSupport
import ink.doa.workbench.agile.workitem.WorkItemFieldPermissionService
import ink.doa.workbench.agile.workitem.WorkItemMutationSupport
import ink.doa.workbench.agile.workitem.WorkItemService
import ink.doa.workbench.agile.workitem.WorkItemTransitionCollaborators
import ink.doa.workbench.agile.workitem.WorkItemTransitionOptionBuilder
import ink.doa.workbench.agile.workitem.WorkItemTransitionService
import ink.doa.workbench.agile.workitem.WorkItemTransitionValidator
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import ink.doa.workbench.core.workitem.IssueSubtypeConstraintRepository
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.WorkflowConfigurationRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock

object AgileServiceFactory {
  fun mockFieldPermissions(): WorkItemFieldPermissionService {
    val fieldPermissions = mockk<WorkItemFieldPermissionService>()
    coEvery { fieldPermissions.canWriteField(any(), any()) } returns true
    coEvery { fieldPermissions.isFieldEditableInTransition(any(), any(), any()) } returns true
    coEvery { fieldPermissions.isFieldEditable(any(), any(), any()) } returns true
    coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } returns true
    return fieldPermissions
  }

  fun workItemService(
    repository: WorkItemRepository,
    configs: IssueTypeConfigRepository,
    events: DomainEventPublisher,
    clock: Clock,
    fieldPermissions: WorkItemFieldPermissionService = mockFieldPermissions(),
    activityEnqueueSupport: WorkItemActivityEnqueueSupport =
      mockk<WorkItemActivityEnqueueSupport>(relaxed = true),
    descriptionAttachmentValidator: WorkItemDescriptionAttachmentValidator =
      mockDescriptionAttachmentValidator(),
  ): WorkItemService {
    val reconciler = WorkItemFieldMutationReconciler(fieldPermissions, clock)
    val subtypeConstraints = mockk<IssueSubtypeConstraintRepository>()
    coEvery { subtypeConstraints.isChildOnlyType(any(), any(), any()) } returns false
    return WorkItemService(
      repository,
      configs,
      subtypeConstraints,
      WorkItemMutationSupport(repository, configs, events),
      activityEnqueueSupport,
      WorkItemFieldMutationSupport(
        reconciler,
        fieldPermissions,
        descriptionAttachmentValidator,
      ),
    )
  }

  fun workItemTransitionService(
    repository: WorkItemRepository,
    configs: IssueTypeConfigRepository,
    workflows: WorkflowConfigurationRepository,
    events: DomainEventPublisher,
    clock: Clock,
    fieldPermissions: WorkItemFieldPermissionService = mockFieldPermissions(),
    activityEnqueueSupport: WorkItemActivityEnqueueSupport =
      mockk<WorkItemActivityEnqueueSupport>(relaxed = true),
    descriptionAttachmentValidator: WorkItemDescriptionAttachmentValidator =
      mockDescriptionAttachmentValidator(),
  ): WorkItemTransitionService {
    val reconciler = WorkItemFieldMutationReconciler(fieldPermissions, clock)
    val mutationSupport = WorkItemMutationSupport(repository, configs, events)
    val transitionValidator = WorkItemTransitionValidator(repository)
    val transitionOptions =
      WorkItemTransitionOptionBuilder(
        mutationSupport,
        reconciler,
        fieldPermissions,
        transitionValidator,
      )
    val collaborators =
      WorkItemTransitionCollaborators(
        mutationSupport,
        activityEnqueueSupport,
        reconciler,
        mockk<WorkItemCommentService>(relaxed = true),
        transitionValidator,
        transitionOptions,
      )
    return WorkItemTransitionService(
      repository,
      workflows,
      collaborators,
      descriptionAttachmentValidator,
    )
  }

  fun mockDescriptionAttachmentValidator(): WorkItemDescriptionAttachmentValidator {
    val validator = mockk<WorkItemDescriptionAttachmentValidator>()
    coEvery { validator.rejectCreateDescriptionReferences(any()) } returns Unit
    coEvery { validator.validateReferences(any(), any(), any(), any(), any()) } returns Unit
    return validator
  }
}
