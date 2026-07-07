package ink.doa.workbench.agile.testfixtures

import ink.doa.workbench.agile.workitem.FieldMutationPolicy
import ink.doa.workbench.agile.workitem.WorkItemActivityEnqueueSupport
import ink.doa.workbench.agile.workitem.WorkItemCommentService
import ink.doa.workbench.agile.workitem.WorkItemCreateParentGuard
import ink.doa.workbench.agile.workitem.WorkItemDescriptionAttachmentValidator
import ink.doa.workbench.agile.workitem.WorkItemFieldMutationEngine
import ink.doa.workbench.agile.workitem.WorkItemFieldMutationFacade
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
    coEvery { fieldPermissions.bindingAllowsWrite(any(), any()) } returns true
    coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns
      FieldMutationPolicy(allowsUserSubmission = true, bindingAllowsWrite = true)
    coEvery { fieldPermissions.resolvePatchPolicy(any(), any()) } returns
      FieldMutationPolicy(allowsUserSubmission = true, bindingAllowsWrite = true)
    return fieldPermissions
  }

  fun fieldMutationEngine(
    clock: Clock,
    fieldPermissions: WorkItemFieldPermissionService = mockFieldPermissions(),
  ): WorkItemFieldMutationEngine = WorkItemFieldMutationEngine(fieldPermissions, clock)

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
    val subtypeConstraints = mockk<IssueSubtypeConstraintRepository>()
    coEvery { subtypeConstraints.isChildOnlyType(any(), any(), any()) } returns false
    return WorkItemService(
      repository,
      configs,
      WorkItemCreateParentGuard(repository, subtypeConstraints),
      WorkItemMutationSupport(repository, configs, events),
      activityEnqueueSupport,
      WorkItemFieldMutationFacade(
        fieldMutationEngine(clock, fieldPermissions),
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
    commentService: WorkItemCommentService = mockk(relaxed = true),
  ): WorkItemTransitionService {
    val engine = fieldMutationEngine(clock, fieldPermissions)
    val mutationSupport = WorkItemMutationSupport(repository, configs, events)
    val transitionValidator = WorkItemTransitionValidator(repository)
    val transitionOptions =
      WorkItemTransitionOptionBuilder(
        mutationSupport,
        engine,
        transitionValidator,
      )
    val collaborators =
      WorkItemTransitionCollaborators(
        mutationSupport,
        activityEnqueueSupport,
        engine,
        commentService,
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
