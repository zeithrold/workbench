package ink.doa.workbench.agile.testfixtures

import ink.doa.workbench.agile.workitem.FieldMutationPolicy
import ink.doa.workbench.agile.workitem.FieldSubmissionPolicy
import ink.doa.workbench.agile.workitem.WorkItemCommentService
import ink.doa.workbench.agile.workitem.WorkItemCreateParentGuard
import ink.doa.workbench.agile.workitem.WorkItemDescriptionAttachmentValidator
import ink.doa.workbench.agile.workitem.WorkItemFieldMutationEngine
import ink.doa.workbench.agile.workitem.WorkItemFieldMutationPipeline
import ink.doa.workbench.agile.workitem.WorkItemFieldPermissionService
import ink.doa.workbench.agile.workitem.WorkItemMutationSupport
import ink.doa.workbench.agile.workitem.WorkItemService
import ink.doa.workbench.agile.workitem.WorkItemTransitionContextLoader
import ink.doa.workbench.agile.workitem.WorkItemTransitionEvaluator
import ink.doa.workbench.agile.workitem.WorkItemTransitionExecutor
import ink.doa.workbench.agile.workitem.WorkItemTransitionOptionBuilder
import ink.doa.workbench.agile.workitem.WorkItemTransitionService
import ink.doa.workbench.agile.workitem.WorkItemTransitionValidator
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import ink.doa.workbench.core.workitem.IssueSubtypeConstraintRepository
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.WorkflowConfigurationRepository
import ink.doa.workbench.core.workitem.template.TransitionFieldsParser
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock

object AgileServiceFactory {
  fun mockFieldPermissions(): WorkItemFieldPermissionService {
    val fieldPermissions = mockk<WorkItemFieldPermissionService>()
    coEvery { fieldPermissions.bindingAllowsWrite(any(), any()) } returns true
    coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns
      FieldMutationPolicy(
        submission = FieldSubmissionPolicy.INHERIT_BINDING,
        bindingAllowsWrite = true,
      )
    coEvery { fieldPermissions.resolvePatchPolicy(any(), any()) } returns
      FieldMutationPolicy(
        submission = FieldSubmissionPolicy.INHERIT_BINDING,
        bindingAllowsWrite = true,
      )
    return fieldPermissions
  }

  fun fieldMutationEngine(
    clock: Clock,
    fieldPermissions: WorkItemFieldPermissionService = mockFieldPermissions(),
  ): WorkItemFieldMutationEngine = WorkItemFieldMutationEngine(fieldPermissions, clock)

  fun fieldMutationPipeline(
    clock: Clock,
    fieldPermissions: WorkItemFieldPermissionService = mockFieldPermissions(),
    transitionFieldsParser: TransitionFieldsParser = TransitionFieldsParser(),
    descriptionAttachmentValidator: WorkItemDescriptionAttachmentValidator =
      mockDescriptionAttachmentValidator(),
  ): WorkItemFieldMutationPipeline =
    WorkItemFieldMutationPipeline(
      engine = fieldMutationEngine(clock, fieldPermissions),
      descriptionAttachments = descriptionAttachmentValidator,
      transitionFieldsParser = transitionFieldsParser,
    )

  fun workItemService(
    repository: WorkItemRepository,
    configs: IssueTypeConfigRepository,
    events: DomainEventPublisher,
    clock: Clock,
    fieldPermissions: WorkItemFieldPermissionService = mockFieldPermissions(),
    descriptionAttachmentValidator: WorkItemDescriptionAttachmentValidator =
      mockDescriptionAttachmentValidator(),
    transitionFieldsParser: TransitionFieldsParser = TransitionFieldsParser(),
  ): WorkItemService {
    val subtypeConstraints = mockk<IssueSubtypeConstraintRepository>()
    coEvery { subtypeConstraints.isChildOnlyType(any(), any(), any()) } returns false
    return WorkItemService(
      repository,
      configs,
      WorkItemCreateParentGuard(repository, subtypeConstraints),
      WorkItemMutationSupport(repository, configs, events),
      fieldMutationPipeline(
        clock,
        fieldPermissions,
        transitionFieldsParser,
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
    descriptionAttachmentValidator: WorkItemDescriptionAttachmentValidator =
      mockDescriptionAttachmentValidator(),
    commentService: WorkItemCommentService = mockk(relaxed = true),
    transitionFieldsParser: TransitionFieldsParser = TransitionFieldsParser(),
  ): WorkItemTransitionService {
    val mutationSupport = WorkItemMutationSupport(repository, configs, events)
    val transitionValidator = WorkItemTransitionValidator(repository)
    val fieldPipeline =
      fieldMutationPipeline(
        clock,
        fieldPermissions,
        transitionFieldsParser,
        descriptionAttachmentValidator,
      )
    return WorkItemTransitionService(
      workflows = workflows,
      contextLoader =
        WorkItemTransitionContextLoader(
          repository,
          mutationSupport,
          transitionValidator,
        ),
      evaluator = WorkItemTransitionEvaluator(transitionValidator, transitionFieldsParser),
      fieldPipeline = fieldPipeline,
      optionBuilder = WorkItemTransitionOptionBuilder(fieldPipeline),
      executor =
        WorkItemTransitionExecutor(
          repository,
          mutationSupport,
          commentService,
        ),
    )
  }

  fun mockDescriptionAttachmentValidator(): WorkItemDescriptionAttachmentValidator {
    val validator = mockk<WorkItemDescriptionAttachmentValidator>()
    coEvery { validator.rejectCreateDescriptionReferences(any()) } returns Unit
    coEvery { validator.validateReferences(any(), any(), any(), any(), any()) } returns Unit
    return validator
  }
}
