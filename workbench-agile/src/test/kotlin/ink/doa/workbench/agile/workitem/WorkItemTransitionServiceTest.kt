package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.testfixtures.AgileServiceFactory
import ink.doa.workbench.agile.testfixtures.AgileWorkItemFixtures
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

class WorkItemTransitionServiceTest :
  StringSpec({
    val clock = Clock.fixed(Instant.parse("2026-07-04T10:15:30Z"), ZoneOffset.UTC)
    val repository = mockk<WorkItemRepository>()
    val configs = mockk<IssueTypeConfigRepository>()
    val workflows = mockk<WorkflowConfigurationRepository>()
    val events = mockk<DomainEventPublisher>()
    val commentService = mockk<WorkItemCommentService>()
    val descriptionAttachmentValidator = AgileServiceFactory.mockDescriptionAttachmentValidator()

    fun service(): WorkItemTransitionService =
      AgileServiceFactory.workItemTransitionService(
        repository = repository,
        configs = configs,
        workflows = workflows,
        events = events,
        clock = clock,
        descriptionAttachmentValidator = descriptionAttachmentValidator,
        commentService = commentService,
      )

    "availableTransitions returns options for current status" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val config = AgileWorkItemFixtures.sampleConfig(tenantId)
      val issue = AgileWorkItemFixtures.sampleIssue(tenantId, projectId, config, actorId)
      val transition = AgileWorkItemFixtures.sampleTransition(config)

      coEvery { repository.findByApiId(tenantId, projectId, issue.apiId.value) } returns issue
      coEvery { configs.findConfig(tenantId, issue.issueTypeConfigApiId.value) } returns config
      coEvery { repository.listPropertyValues(tenantId, issue.id) } returns emptyMap()
      coEvery {
        repository.countChildrenNotInStatusGroups(tenantId, issue.id, setOf("done"))
      } returns 0
      coEvery { workflows.listTransitions(any(), any()) } returns listOf(transition)
      coEvery { repository.resolveUserApiId(actorId) } returns PublicId.new("usr")
      coEvery { repository.resolveProjectApiId(tenantId, projectId) } returns PublicId.new("prj")

      val options = runBlocking {
        service().availableTransitions(tenantId, projectId, issue.apiId.value, actorId)
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
            service()
              .transition(
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
      coEvery { configs.findConfig(tenantId, issue.issueTypeConfigApiId.value) } returns config
      coEvery { workflows.findTransition(tenantId, "trn_missing") } returns null

      shouldThrow<ResourceNotFoundException> {
          runBlocking {
            service()
              .transition(
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
      coEvery { configs.findConfig(tenantId, issue.issueTypeConfigApiId.value) } returns config
      coEvery { repository.listPropertyValues(tenantId, issue.id) } returns emptyMap()
      coEvery {
        repository.countChildrenNotInStatusGroups(tenantId, issue.id, setOf("done"))
      } returns 0
      coEvery { workflows.listTransitions(any(), any()) } returns listOf(mismatchedTransition)

      val options = runBlocking {
        service().availableTransitions(tenantId, projectId, issue.apiId.value, actorId)
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
      coEvery { configs.findConfig(tenantId, issue.issueTypeConfigApiId.value) } returns config
      coEvery { workflows.findTransition(tenantId, transition.apiId.value) } returns transition
      coEvery { repository.listPropertyValues(tenantId, issue.id) } returns emptyMap()
      coEvery {
        repository.countChildrenNotInStatusGroups(tenantId, issue.id, setOf("done"))
      } returns 0
      coEvery { repository.resolveUserApiId(actorId) } returns PublicId.new("usr")
      coEvery { repository.resolveProjectApiId(tenantId, projectId) } returns PublicId.new("prj")
      coEvery {
        repository.transition(any(), any(), any(), any(), any())
      } returns mutationResult
      justRun { events.publish<Any>(any(), any(), any(), any()) }

      val result = runBlocking {
        service()
          .transition(
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
    }

    "transition comment links to status activity id" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val config = AgileWorkItemFixtures.sampleConfig(tenantId)
      val issue = AgileWorkItemFixtures.sampleIssue(tenantId, projectId, config, actorId)
      val transition =
        AgileWorkItemFixtures.sampleTransition(config)
          .copy(
            fields =
              kotlinx.serialization.json.Json.parseToJsonElement(
                  """
                  {
                    "version": 1,
                    "resource": "work_item",
                    "target": "transition",
                    "fields": {},
                    "comment": { "participation": "optional" }
                  }
                  """
                    .trimIndent()
                )
                .let { it as kotlinx.serialization.json.JsonObject }
          )
      val activityId = UUID.randomUUID()
      val mutationResult =
        ink.doa.workbench.core.workitem.model.WorkItemMutationResult(
          issue,
          "work_item.transitioned",
          statusHistoryId = UUID.randomUUID(),
          activityId = activityId,
        )

      coEvery { repository.findByApiId(tenantId, projectId, issue.apiId.value) } returns issue
      coEvery { configs.findConfig(tenantId, issue.issueTypeConfigApiId.value) } returns config
      coEvery { workflows.findTransition(tenantId, transition.apiId.value) } returns transition
      coEvery { repository.listPropertyValues(tenantId, issue.id) } returns emptyMap()
      coEvery {
        repository.countChildrenNotInStatusGroups(tenantId, issue.id, setOf("done"))
      } returns 0
      coEvery { repository.resolveUserApiId(actorId) } returns PublicId.new("usr")
      coEvery { repository.resolveProjectApiId(tenantId, projectId) } returns PublicId.new("prj")
      coEvery {
        repository.transition(any(), any(), any(), any(), any())
      } returns mutationResult
      justRun { events.publish<Any>(any(), any(), any(), any()) }
      val commentSlot = slot<CreateWorkItemCommentCommand>()
      coEvery { commentService.create(capture(commentSlot)) } returns mockk(relaxed = true)

      runBlocking {
        service()
          .transition(
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
            service().availableTransitions(tenantId, projectId, "iss_missing", UUID.randomUUID())
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
      coEvery { configs.findConfig(tenantId, issue.issueTypeConfigApiId.value) } returns config
      coEvery { workflows.findTransition(tenantId, transition.apiId.value) } returns transition
      coEvery { repository.listPropertyValues(tenantId, issue.id) } returns emptyMap()
      coEvery {
        repository.countChildrenNotInStatusGroups(tenantId, issue.id, setOf("done"))
      } returns 0
      coEvery { repository.resolveUserApiId(actorId) } returns PublicId.new("usr")
      coEvery { repository.resolveProjectApiId(tenantId, projectId) } returns PublicId.new("prj")
      coEvery {
        repository.transition(any(), any(), any(), any(), any())
      } returns mutationResult
      justRun { events.publish<Any>(any(), any(), any(), any()) }

      runBlocking {
        service()
          .transition(
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
