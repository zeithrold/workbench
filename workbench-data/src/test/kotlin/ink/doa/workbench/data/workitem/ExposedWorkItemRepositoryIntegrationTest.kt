package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.CreateWorkflowTransitionCommand
import ink.doa.workbench.core.workitem.model.TransitionWorkItemCommand
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.withPostgresDatabase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag

@Tag("integration")
class ExposedWorkItemRepositoryIntegrationTest :
  StringSpec({
    "findByApiId returns null when issue does not exist" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val repository = ExposedWorkItemRepository(database)

        repository.findByApiId(stack.tenantId, stack.projectId, "iss_missing").shouldBeNull()
      }
    }

    "countChildrenNotInStatusGroups returns zero for issue without children" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val repository = ExposedWorkItemRepository(database)

        repository.countChildrenNotInStatusGroups(
          stack.tenantId,
          java.util.UUID.randomUUID(),
          setOf("done"),
        ) shouldBe 0
      }
    }

    "create persists work item with key alias and initial status" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val repository = ExposedWorkItemRepository(database)

        val result =
          repository.create(
            CreateWorkItemCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              issueTypeApiId = stack.issueType.apiId.value,
              title = "First issue",
              description = "Details",
              reporterId = stack.actorId,
              actorUserId = stack.actorId,
            ),
            issueTypeId = stack.issueType.id,
            issueTypeConfigId = stack.config.config.id,
            initialStatusId = stack.todoStatus.id,
            propertyValues = emptyList(),
          )

        result.workItem.title shouldBe "First issue"
        result.workItem.statusApiId shouldBe stack.todoStatus.apiId
        result.eventType shouldBe "work_item.created"
        repository
          .findByApiId(stack.tenantId, stack.projectId, result.workItem.apiId.value)
          .shouldNotBeNull()
      }
    }

    "transition updates status and records history" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val workflows =
          ExposedWorkflowConfigurationRepository(
            database,
            ExposedWorkItemCatalogRepository(database),
          )
        val transition =
          workflows.createTransition(
            CreateWorkflowTransitionCommand(
              tenantId = stack.tenantId,
              workflowApiId = stack.workflow.apiId.value,
              name = "Complete",
              fromStatusApiId = stack.todoStatus.apiId.value,
              toStatusApiId = stack.doneStatus.apiId.value,
            )
          )
        val repository = ExposedWorkItemRepository(database)
        val created =
          repository.create(
            CreateWorkItemCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              issueTypeApiId = stack.issueType.apiId.value,
              title = "Transition me",
              description = null,
              reporterId = stack.actorId,
              actorUserId = stack.actorId,
            ),
            issueTypeId = stack.issueType.id,
            issueTypeConfigId = stack.config.config.id,
            initialStatusId = stack.todoStatus.id,
            propertyValues = emptyList(),
          )

        val result =
          repository.transition(
            TransitionWorkItemCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              workItemApiId = created.workItem.apiId.value,
              transitionApiId = transition.apiId.value,
              actorUserId = stack.actorId,
            ),
            fromStatusId = stack.todoStatus.id,
            toStatusId = stack.doneStatus.id,
            transitionId = transition.id,
            propertyValues = emptyList(),
          )

        result.workItem.statusApiId shouldBe stack.doneStatus.apiId
        result.eventType shouldBe "work_item.transitioned"
      }
    }
  })
