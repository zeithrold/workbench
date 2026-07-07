package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.testfixtures.AgileServiceFactory
import ink.doa.workbench.agile.testfixtures.AgileWorkItemFixtures
import ink.doa.workbench.agile.testfixtures.TransitionTestHarness
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommentCommand
import ink.doa.workbench.core.workitem.model.TransitionRequest
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class WorkItemTransitionServiceTest :
  StringSpec({
    val harness = TransitionTestHarness()
    val commentService = mockk<WorkItemCommentService>()

    fun service() =
      AgileServiceFactory.workItemTransitionService(
        repository = harness.repository,
        configs = harness.configs,
        workflows = harness.workflows,
        events = harness.events,
        clock = harness.clock,
        transitionFieldsParser = harness.transitionFieldsParser,
        commentService = commentService,
      )

    fun stubTransitionContext(
      tenantId: UUID,
      projectId: UUID,
      actorId: UUID,
      issue: ink.doa.workbench.core.workitem.model.WorkItemRecord,
      config: ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails,
    ) {
      coEvery { harness.repository.listPropertyValues(tenantId, issue.id) } returns emptyMap()
      coEvery {
        harness.repository.countChildrenNotInStatusGroups(tenantId, issue.id, setOf("done"))
      } returns 0
      coEvery { harness.repository.resolveUserApiId(actorId) } returns PublicId.new("usr")
      coEvery { harness.repository.resolveProjectApiId(tenantId, projectId) } returns
        PublicId.new("prj")
      coEvery { harness.configs.findConfig(tenantId, issue.issueTypeConfigApiId.value) } returns
        config
    }

    "availableTransitions returns options for current status" {
      runTest {
        val tenantId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val config = AgileWorkItemFixtures.sampleConfig(tenantId)
        val issue = AgileWorkItemFixtures.sampleIssue(tenantId, projectId, config, actorId)
        val transition = AgileWorkItemFixtures.sampleTransition(config)

        coEvery { harness.repository.findByApiId(tenantId, projectId, issue.apiId.value) } returns
          issue
        stubTransitionContext(tenantId, projectId, actorId, issue, config)
        coEvery { harness.workflows.listTransitions(any(), any()) } returns listOf(transition)

        val options =
          service()
            .availableTransitions(
              tenantId,
              projectId,
              issue.apiId.value,
              actorId,
              "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
            )

        options shouldHaveSize 1
        options.single().id shouldBe transition.apiId
      }
    }

    "transition rejects unknown work item" {
      runTest {
        val tenantId = UUID.randomUUID()
        val projectId = UUID.randomUUID()

        coEvery { harness.repository.findByApiId(tenantId, projectId, "iss_missing") } returns null

        shouldThrow<ResourceNotFoundException> {
            service()
              .transition(
                TransitionRequest(
                  tenantId = tenantId,
                  projectId = projectId,
                  workItemApiId = "iss_missing",
                  transitionApiId = "trn_done",
                  actorUserId = UUID.randomUUID(),
                  actorUserApiId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
                )
              )
          }
          .errorCode shouldBe WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND
      }
    }

    "transition rejects unknown workflow transition" {
      runTest {
        val tenantId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val config = AgileWorkItemFixtures.sampleConfig(tenantId)
        val issue = AgileWorkItemFixtures.sampleIssue(tenantId, projectId, config, actorId)

        coEvery { harness.repository.findByApiId(tenantId, projectId, issue.apiId.value) } returns
          issue
        stubTransitionContext(tenantId, projectId, actorId, issue, config)
        coEvery { harness.workflows.findTransition(tenantId, "trn_missing") } returns null

        shouldThrow<ResourceNotFoundException> {
            service()
              .transition(
                TransitionRequest(
                  tenantId = tenantId,
                  projectId = projectId,
                  workItemApiId = issue.apiId.value,
                  transitionApiId = "trn_missing",
                  actorUserId = actorId,
                  actorUserApiId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
                )
              )
          }
          .errorCode shouldBe WorkbenchErrorCode.RESOURCE_WORKFLOW_TRANSITION_NOT_FOUND
      }
    }

    "availableTransitions returns empty list when transitions do not match current status" {
      runTest {
        val tenantId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val config = AgileWorkItemFixtures.sampleConfig(tenantId)
        val issue = AgileWorkItemFixtures.sampleIssue(tenantId, projectId, config, actorId)
        val otherStatusId = UUID.randomUUID()
        val mismatchedTransition =
          AgileWorkItemFixtures.sampleTransition(config)
            .copy(fromStatusId = otherStatusId, fromStatusApiId = PublicId.new("sts"))

        coEvery { harness.repository.findByApiId(tenantId, projectId, issue.apiId.value) } returns
          issue
        stubTransitionContext(tenantId, projectId, actorId, issue, config)
        coEvery { harness.workflows.listTransitions(any(), any()) } returns
          listOf(mismatchedTransition)

        val options =
          service()
            .availableTransitions(
              tenantId,
              projectId,
              issue.apiId.value,
              actorId,
              "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
            )

        options.shouldBeEmpty()
      }
    }

    "transition executes and returns mutation result" {
      runTest {
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

        coEvery { harness.repository.findByApiId(tenantId, projectId, issue.apiId.value) } returns
          issue
        stubTransitionContext(tenantId, projectId, actorId, issue, config)
        coEvery { harness.workflows.findTransition(tenantId, transition.apiId.value) } returns
          transition
        coEvery {
          harness.repository.transition(any(), any(), any(), any(), any())
        } returns mutationResult
        justRun { harness.events.publish<Any>(any(), any(), any(), any()) }

        val result =
          service()
            .transition(
              TransitionRequest(
                tenantId = tenantId,
                projectId = projectId,
                workItemApiId = issue.apiId.value,
                transitionApiId = transition.apiId.value,
                actorUserId = actorId,
                actorUserApiId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
              )
            )

        result.eventType shouldBe "work_item.transitioned"
      }
    }

    "transition comment links to status history id" {
      runTest {
        val tenantId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val config = AgileWorkItemFixtures.sampleConfig(tenantId)
        val issue = AgileWorkItemFixtures.sampleIssue(tenantId, projectId, config, actorId)
        val transition =
          AgileWorkItemFixtures.sampleTransition(config)
            .copy(
              fields =
                Json.parseToJsonElement(
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
                  .jsonObject
            )
        val mutationResult =
          ink.doa.workbench.core.workitem.model.WorkItemMutationResult(
            issue,
            "work_item.transitioned",
            statusHistoryId = UUID.randomUUID(),
            streamEventId = UUID.randomUUID(),
            streamEventApiId = PublicId.new("evt"),
          )

        coEvery { harness.repository.findByApiId(tenantId, projectId, issue.apiId.value) } returns
          issue
        stubTransitionContext(tenantId, projectId, actorId, issue, config)
        coEvery { harness.workflows.findTransition(tenantId, transition.apiId.value) } returns
          transition
        coEvery {
          harness.repository.transition(any(), any(), any(), any(), any())
        } returns mutationResult
        justRun { harness.events.publish<Any>(any(), any(), any(), any()) }
        val commentSlot = slot<CreateWorkItemCommentCommand>()
        coEvery { commentService.create(capture(commentSlot)) } returns mockk(relaxed = true)

        service()
          .transition(
            TransitionRequest(
              tenantId = tenantId,
              projectId = projectId,
              workItemApiId = issue.apiId.value,
              transitionApiId = transition.apiId.value,
              actorUserId = actorId,
              actorUserApiId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
              comment = "Looks good",
            )
          )

        commentSlot.captured.transitionId shouldBe transition.id
        commentSlot.captured.statusHistoryId shouldBe mutationResult.statusHistoryId
      }
    }

    "availableTransitions rejects unknown work item" {
      runTest {
        val tenantId = UUID.randomUUID()
        val projectId = UUID.randomUUID()

        coEvery { harness.repository.findByApiId(tenantId, projectId, "iss_missing") } returns null

        shouldThrow<ResourceNotFoundException> {
            service()
              .availableTransitions(
                tenantId,
                projectId,
                "iss_missing",
                UUID.randomUUID(),
                "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
              )
          }
          .errorCode shouldBe WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND
      }
    }

    "transition validates description attachment references" {
      runTest {
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
        val descriptionAttachmentValidator =
          AgileServiceFactory.mockDescriptionAttachmentValidator()

        coEvery { harness.repository.findByApiId(tenantId, projectId, issue.apiId.value) } returns
          issue
        stubTransitionContext(tenantId, projectId, actorId, issue, config)
        coEvery { harness.workflows.findTransition(tenantId, transition.apiId.value) } returns
          transition
        coEvery {
          harness.repository.transition(any(), any(), any(), any(), any())
        } returns mutationResult
        justRun { harness.events.publish<Any>(any(), any(), any(), any()) }

        AgileServiceFactory.workItemTransitionService(
            repository = harness.repository,
            configs = harness.configs,
            workflows = harness.workflows,
            events = harness.events,
            clock = harness.clock,
            transitionFieldsParser = harness.transitionFieldsParser,
            descriptionAttachmentValidator = descriptionAttachmentValidator,
          )
          .transition(
            TransitionRequest(
              tenantId = tenantId,
              projectId = projectId,
              workItemApiId = issue.apiId.value,
              transitionApiId = transition.apiId.value,
              actorUserId = actorId,
              actorUserApiId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
            )
          )

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
    }
  })
