package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.testfixtures.AgileWorkItemFixtures
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.WorkflowConfigurationRepository
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommentCommand
import ink.doa.workbench.core.workitem.model.TransitionWorkItemCommand
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import java.util.UUID
import kotlinx.coroutines.runBlocking

class WorkItemTransitionServiceTest :
  StringSpec({
    val repository = mockk<WorkItemRepository>()
    val workflows = mockk<WorkflowConfigurationRepository>()
    val mutationSupport = mockk<WorkItemMutationSupport>()
    val activityEnqueueSupport = mockk<WorkItemActivityEnqueueSupport>(relaxed = true)
    val fieldMutationReconciler = mockk<WorkItemFieldMutationReconciler>()
    val commentService = mockk<WorkItemCommentService>()
    val fieldPermissions = mockk<WorkItemFieldPermissionService>()
    val transitionValidator = WorkItemTransitionValidator(repository)
    val transitionOptions =
      WorkItemTransitionOptionBuilder(
        mutationSupport,
        fieldMutationReconciler,
        fieldPermissions,
        transitionValidator,
      )
    val collaborators =
      WorkItemTransitionCollaborators(
        mutationSupport,
        activityEnqueueSupport,
        fieldMutationReconciler,
        commentService,
        transitionValidator,
        transitionOptions,
      )
    val descriptionAttachmentValidator = mockk<WorkItemDescriptionAttachmentValidator>()
    coEvery {
      descriptionAttachmentValidator.validateReferences(any(), any(), any(), any(), any())
    } returns Unit
    val service =
      WorkItemTransitionService(
        repository,
        workflows,
        collaborators,
        descriptionAttachmentValidator,
      )
    "availableTransitions returns options for current status" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val config = AgileWorkItemFixtures.sampleConfig(tenantId)
      val issue = AgileWorkItemFixtures.sampleIssue(tenantId, projectId, config, actorId)
      val transition = AgileWorkItemFixtures.sampleTransition(config)

      coEvery { repository.findByApiId(tenantId, projectId, issue.apiId.value) } returns issue
      coEvery { mutationSupport.requireConfig(tenantId, issue.issueTypeConfigApiId.value) } returns
        config
      coEvery { repository.listPropertyValues(tenantId, issue.id) } returns emptyMap()
      coEvery {
        repository.countChildrenNotInStatusGroups(tenantId, issue.id, setOf("done"))
      } returns 0
      coEvery { workflows.listTransitions(any(), any()) } returns listOf(transition)
      coEvery {
        fieldPermissions.isFormFieldEditable(any(), any(), any())
      } returns true
      coEvery { mutationSupport.templateContext(any()) } returns mockk(relaxed = true)
      coEvery { fieldMutationReconciler.buildFieldMeta(any(), any(), any(), any()) } returns
        emptyList()
      coEvery { fieldMutationReconciler.buildCommentMeta(any(), any()) } returns null

      val options = runBlocking {
        service.availableTransitions(tenantId, projectId, issue.apiId.value, actorId)
      }

      options shouldHaveSize 1
      options.single().id shouldBe transition.apiId
    }

    "transition rejects unknown work item" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()

      coEvery { repository.findByApiId(tenantId, projectId, "iss_missing") } returns null

      shouldThrow<ResourceNotFoundException> {
          runBlocking {
            service.transition(
              TransitionWorkItemCommand(
                tenantId = tenantId,
                projectId = projectId,
                workItemApiId = "iss_missing",
                transitionApiId = "trn_done",
                actorUserId = UUID.randomUUID(),
              )
            )
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND
    }

    "transition rejects unknown workflow transition" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val config = AgileWorkItemFixtures.sampleConfig(tenantId)
      val issue = AgileWorkItemFixtures.sampleIssue(tenantId, projectId, config, actorId)

      coEvery { repository.findByApiId(tenantId, projectId, issue.apiId.value) } returns issue
      coEvery { mutationSupport.requireConfig(tenantId, issue.issueTypeConfigApiId.value) } returns
        config
      coEvery { workflows.findTransition(tenantId, "trn_missing") } returns null

      shouldThrow<ResourceNotFoundException> {
          runBlocking {
            service.transition(
              TransitionWorkItemCommand(
                tenantId = tenantId,
                projectId = projectId,
                workItemApiId = issue.apiId.value,
                transitionApiId = "trn_missing",
                actorUserId = actorId,
              )
            )
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_WORKFLOW_TRANSITION_NOT_FOUND
    }

    "availableTransitions returns empty list when transitions do not match current status" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val config = AgileWorkItemFixtures.sampleConfig(tenantId)
      val issue = AgileWorkItemFixtures.sampleIssue(tenantId, projectId, config, actorId)
      val otherStatusId = UUID.randomUUID()
      val mismatchedTransition =
        AgileWorkItemFixtures.sampleTransition(config)
          .copy(fromStatusId = otherStatusId, fromStatusApiId = PublicId.new("sts"))

      coEvery { repository.findByApiId(tenantId, projectId, issue.apiId.value) } returns issue
      coEvery { mutationSupport.requireConfig(tenantId, issue.issueTypeConfigApiId.value) } returns
        config
      coEvery { repository.listPropertyValues(tenantId, issue.id) } returns emptyMap()
      coEvery {
        repository.countChildrenNotInStatusGroups(tenantId, issue.id, setOf("done"))
      } returns 0
      coEvery { workflows.listTransitions(any(), any()) } returns listOf(mismatchedTransition)

      val options = runBlocking {
        service.availableTransitions(tenantId, projectId, issue.apiId.value, actorId)
      }

      options.shouldBeEmpty()
    }

    "transition executes and returns mutation result" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val config = AgileWorkItemFixtures.sampleConfig(tenantId)
      val issue = AgileWorkItemFixtures.sampleIssue(tenantId, projectId, config, actorId)
      val transition = AgileWorkItemFixtures.sampleTransition(config)
      val mutationResult =
        ink.doa.workbench.core.workitem.model.WorkItemMutationResult(
          issue,
          "work_item.transitioned",
        )

      coEvery { repository.findByApiId(tenantId, projectId, issue.apiId.value) } returns issue
      coEvery { mutationSupport.requireConfig(tenantId, issue.issueTypeConfigApiId.value) } returns
        config
      coEvery { workflows.findTransition(tenantId, transition.apiId.value) } returns transition
      coEvery { repository.listPropertyValues(tenantId, issue.id) } returns emptyMap()
      coEvery {
        repository.countChildrenNotInStatusGroups(tenantId, issue.id, setOf("done"))
      } returns 0
      coEvery { mutationSupport.templateContext(any()) } returns mockk(relaxed = true)
      coEvery { fieldMutationReconciler.buildFieldMeta(any(), any(), any(), any()) } returns
        emptyList()
      coEvery { fieldMutationReconciler.buildCommentMeta(any(), any()) } returns null
      coEvery {
        fieldMutationReconciler.reconcileFields(any())
      } returns
        TransitionFieldReconcileResult(propertyValues = emptyMap(), systemFields = emptyMap())
      coEvery {
        fieldMutationReconciler.reconcileTransitionComment(any(), any(), any())
      } returns null
      coEvery {
        repository.transition(any(), any(), any(), any(), any())
      } returns mutationResult
      justRun { mutationSupport.publishAndEnqueue(any(), any()) }

      val result = runBlocking {
        service.transition(
          TransitionWorkItemCommand(
            tenantId = tenantId,
            projectId = projectId,
            workItemApiId = issue.apiId.value,
            transitionApiId = transition.apiId.value,
            actorUserId = actorId,
          )
        )
      }

      result.eventType shouldBe "work_item.transitioned"
      coVerify { mutationSupport.publishAndEnqueue(mutationResult, activityEnqueueSupport) }
    }

    "transition comment links to status activity id" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val config = AgileWorkItemFixtures.sampleConfig(tenantId)
      val issue = AgileWorkItemFixtures.sampleIssue(tenantId, projectId, config, actorId)
      val transition = AgileWorkItemFixtures.sampleTransition(config)
      val activityId = UUID.randomUUID()
      val mutationResult =
        ink.doa.workbench.core.workitem.model.WorkItemMutationResult(
          issue,
          "work_item.transitioned",
          statusHistoryId = UUID.randomUUID(),
          activityId = activityId,
        )

      coEvery { repository.findByApiId(tenantId, projectId, issue.apiId.value) } returns issue
      coEvery { mutationSupport.requireConfig(tenantId, issue.issueTypeConfigApiId.value) } returns
        config
      coEvery { workflows.findTransition(tenantId, transition.apiId.value) } returns transition
      coEvery { repository.listPropertyValues(tenantId, issue.id) } returns emptyMap()
      coEvery {
        repository.countChildrenNotInStatusGroups(tenantId, issue.id, setOf("done"))
      } returns 0
      coEvery { mutationSupport.templateContext(any()) } returns mockk(relaxed = true)
      coEvery { fieldMutationReconciler.buildFieldMeta(any(), any(), any(), any()) } returns
        emptyList()
      coEvery { fieldMutationReconciler.buildCommentMeta(any(), any()) } returns null
      coEvery {
        fieldMutationReconciler.reconcileFields(any())
      } returns
        TransitionFieldReconcileResult(propertyValues = emptyMap(), systemFields = emptyMap())
      coEvery {
        fieldMutationReconciler.reconcileTransitionComment(any(), any(), any())
      } returns "Looks good"
      coEvery {
        repository.transition(any(), any(), any(), any(), any())
      } returns mutationResult
      justRun { mutationSupport.publishAndEnqueue(any(), any()) }
      val commentSlot = slot<CreateWorkItemCommentCommand>()
      coEvery { commentService.create(capture(commentSlot)) } returns mockk(relaxed = true)

      runBlocking {
        service.transition(
          TransitionWorkItemCommand(
            tenantId = tenantId,
            projectId = projectId,
            workItemApiId = issue.apiId.value,
            transitionApiId = transition.apiId.value,
            actorUserId = actorId,
            comment = "Looks good",
          )
        )
      }

      commentSlot.captured.activityId shouldBe activityId
      commentSlot.captured.statusHistoryId shouldBe mutationResult.statusHistoryId
    }

    "availableTransitions rejects unknown work item" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()

      coEvery { repository.findByApiId(tenantId, projectId, "iss_missing") } returns null

      shouldThrow<ResourceNotFoundException> {
          runBlocking {
            service.availableTransitions(tenantId, projectId, "iss_missing", UUID.randomUUID())
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND
    }

    "transition validates description attachment references" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val config = AgileWorkItemFixtures.sampleConfig(tenantId)
      val issue = AgileWorkItemFixtures.sampleIssue(tenantId, projectId, config, actorId)
      val transition = AgileWorkItemFixtures.sampleTransition(config)
      val mutationResult =
        ink.doa.workbench.core.workitem.model.WorkItemMutationResult(
          issue,
          "work_item.transitioned",
        )

      coEvery { repository.findByApiId(tenantId, projectId, issue.apiId.value) } returns issue
      coEvery { mutationSupport.requireConfig(tenantId, issue.issueTypeConfigApiId.value) } returns
        config
      coEvery { workflows.findTransition(tenantId, transition.apiId.value) } returns transition
      coEvery { repository.listPropertyValues(tenantId, issue.id) } returns emptyMap()
      coEvery {
        repository.countChildrenNotInStatusGroups(tenantId, issue.id, setOf("done"))
      } returns 0
      coEvery { mutationSupport.templateContext(any()) } returns mockk(relaxed = true)
      coEvery { fieldMutationReconciler.buildFieldMeta(any(), any(), any(), any()) } returns
        emptyList()
      coEvery { fieldMutationReconciler.buildCommentMeta(any(), any()) } returns null
      coEvery {
        fieldMutationReconciler.reconcileFields(any())
      } returns
        TransitionFieldReconcileResult(propertyValues = emptyMap(), systemFields = emptyMap())
      coEvery {
        fieldMutationReconciler.reconcileTransitionComment(any(), any(), any())
      } returns null
      coEvery {
        repository.transition(any(), any(), any(), any(), any())
      } returns mutationResult
      justRun { mutationSupport.publishAndEnqueue(any(), any()) }

      runBlocking {
        service.transition(
          TransitionWorkItemCommand(
            tenantId = tenantId,
            projectId = projectId,
            workItemApiId = issue.apiId.value,
            transitionApiId = transition.apiId.value,
            actorUserId = actorId,
          )
        )
      }

      coVerify {
        descriptionAttachmentValidator.validateReferences(
          tenantId,
          projectId,
          issue.apiId.value,
          issue.id,
          null,
        )
      }
    }
  })
