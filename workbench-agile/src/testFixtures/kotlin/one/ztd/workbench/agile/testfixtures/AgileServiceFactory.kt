package one.ztd.workbench.agile.testfixtures

import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import one.ztd.workbench.agile.workitem.IssueSubtypeConstraintRepository
import one.ztd.workbench.agile.workitem.IssueTypeConfigRepository
import one.ztd.workbench.agile.workitem.WorkItemAccessPolicyEngine
import one.ztd.workbench.agile.workitem.WorkItemCommentService
import one.ztd.workbench.agile.workitem.WorkItemCreateParentGuard
import one.ztd.workbench.agile.workitem.WorkItemFieldMutationEngine
import one.ztd.workbench.agile.workitem.WorkItemFieldMutationPipeline
import one.ztd.workbench.agile.workitem.WorkItemFieldPermissionService
import one.ztd.workbench.agile.workitem.WorkItemMutationSupport
import one.ztd.workbench.agile.workitem.WorkItemRepository
import one.ztd.workbench.agile.workitem.WorkItemService
import one.ztd.workbench.agile.workitem.WorkItemTransitionContextLoader
import one.ztd.workbench.agile.workitem.WorkItemTransitionEvaluator
import one.ztd.workbench.agile.workitem.WorkItemTransitionExecutor
import one.ztd.workbench.agile.workitem.WorkItemTransitionOptionBuilder
import one.ztd.workbench.agile.workitem.WorkItemTransitionService
import one.ztd.workbench.agile.workitem.WorkItemTransitionValidator
import one.ztd.workbench.agile.workitem.WorkflowConfigurationRepository
import one.ztd.workbench.agile.workitem.access.WorkItemAccessActor
import one.ztd.workbench.agile.workitem.template.TransitionFieldsParser
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.identity.permission.model.AuthorizationAction
import one.ztd.workbench.kernel.port.messaging.DomainEventPublisher

object AgileServiceFactory {
  fun mockAccessPolicy(): WorkItemAccessPolicyEngine {
    val accessPolicy = mockk<WorkItemAccessPolicyEngine>(relaxed = true)
    coEvery { accessPolicy.resolveActor(any(), any(), any()) } returns
      WorkItemAccessActor(
        userId = java.util.UUID.randomUUID(),
        userApiId = "usr_test",
        groupIds = emptySet(),
        projectRoles = setOf("admin", "member"),
      )
    coEvery { accessPolicy.isTransitionPermitted(any(), any(), any()) } returns true
    coEvery { accessPolicy.isFieldWritePermitted(any(), any(), any()) } returns true
    coEvery { accessPolicy.isCommentPermitted(any(), any()) } returns true
    coEvery { accessPolicy.bindingAllowsFieldWrite(any(), any(), any()) } returns true
    listOf("issue.comment.create", "issue.comment.update", "issue.comment.delete").forEach { action
      ->
      coEvery {
        accessPolicy.bindingAllowsComment(any(), any(), any(), AuthorizationAction(action))
      } returns true
    }
    return accessPolicy
  }

  fun mockFieldPermissions(
    accessPolicy: WorkItemAccessPolicyEngine = mockAccessPolicy()
  ): WorkItemFieldPermissionService = WorkItemFieldPermissionService(accessPolicy)

  fun fieldMutationEngine(
    clock: Clock,
    fieldPermissions: WorkItemFieldPermissionService = mockFieldPermissions(),
  ): WorkItemFieldMutationEngine = WorkItemFieldMutationEngine(fieldPermissions, clock)

  fun fieldMutationPipeline(
    clock: Clock,
    fieldPermissions: WorkItemFieldPermissionService = mockFieldPermissions(),
    transitionFieldsParser: TransitionFieldsParser = TransitionFieldsParser(),
  ): WorkItemFieldMutationPipeline =
    WorkItemFieldMutationPipeline(
      engine = fieldMutationEngine(clock, fieldPermissions),
      transitionFieldsParser = transitionFieldsParser,
    )

  fun workItemService(
    repository: WorkItemRepository,
    configs: IssueTypeConfigRepository,
    events: DomainEventPublisher,
    clock: Clock,
    fieldPermissions: WorkItemFieldPermissionService = mockFieldPermissions(),
    transitionFieldsParser: TransitionFieldsParser = TransitionFieldsParser(),
  ): WorkItemService {
    val subtypeConstraints = mockk<IssueSubtypeConstraintRepository>()
    coEvery { subtypeConstraints.isChildOnlyType(any(), any(), any()) } returns false
    val users = mockk<UserRepository>()
    coEvery { users.findById(any()) } returns
      UserRecord(
        id = java.util.UUID.randomUUID(),
        apiId = one.ztd.workbench.kernel.common.ids.PublicId.new("usr"),
        displayName = "Test User",
        primaryEmail = "test@example.com",
      )
    return WorkItemService(
      repository,
      configs,
      users,
      WorkItemCreateParentGuard(repository, subtypeConstraints),
      WorkItemMutationSupport(repository, configs, events),
      fieldMutationPipeline(
        clock,
        fieldPermissions,
        transitionFieldsParser,
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
    accessPolicy: WorkItemAccessPolicyEngine = mockAccessPolicy(),
    commentService: WorkItemCommentService = mockk(relaxed = true),
    transitionFieldsParser: TransitionFieldsParser = TransitionFieldsParser(),
  ): WorkItemTransitionService {
    val mutationSupport = WorkItemMutationSupport(repository, configs, events)
    val transitionValidator = WorkItemTransitionValidator(repository, accessPolicy)
    val fieldPipeline =
      fieldMutationPipeline(
        clock,
        fieldPermissions,
        transitionFieldsParser,
      )
    return WorkItemTransitionService(
      workflows = workflows,
      contextLoader =
        WorkItemTransitionContextLoader(
          repository,
          mutationSupport,
          transitionValidator,
        ),
      evaluator =
        WorkItemTransitionEvaluator(
          transitionValidator,
          accessPolicy,
          transitionFieldsParser,
        ),
      fieldPipeline = fieldPipeline,
      optionBuilder = WorkItemTransitionOptionBuilder(fieldPipeline),
      executor =
        WorkItemTransitionExecutor(
          repository,
          commentService,
        ),
    )
  }
}
