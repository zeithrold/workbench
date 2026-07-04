package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.workitem.model.CreatePropertyDefinitionCommand
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.CreateWorkflowTransitionCommand
import ink.doa.workbench.core.workitem.model.TransitionWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.withPostgresDatabase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive

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

    "update persists title changes" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val repository = ExposedWorkItemRepository(database)
        val created =
          repository.create(
            CreateWorkItemCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              issueTypeApiId = stack.issueType.apiId.value,
              title = "Original",
              description = null,
              reporterId = stack.actorId,
              actorUserId = stack.actorId,
            ),
            issueTypeId = stack.issueType.id,
            issueTypeConfigId = stack.config.config.id,
            initialStatusId = stack.todoStatus.id,
            propertyValues = emptyList(),
          )

        val updated =
          repository.update(
            ink.doa.workbench.core.workitem.model.UpdateWorkItemCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              workItemApiId = created.workItem.apiId.value,
              title = "Updated title",
              actorUserId = stack.actorId,
            ),
            propertyValues = emptyList(),
          )

        updated.workItem.title shouldBe "Updated title"
        updated.eventType shouldBe "work_item.updated"
      }
    }

    "softDelete marks work item deleted" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val repository = ExposedWorkItemRepository(database)
        val created =
          repository.create(
            CreateWorkItemCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              issueTypeApiId = stack.issueType.apiId.value,
              title = "Delete me",
              description = null,
              reporterId = stack.actorId,
              actorUserId = stack.actorId,
            ),
            issueTypeId = stack.issueType.id,
            issueTypeConfigId = stack.config.config.id,
            initialStatusId = stack.todoStatus.id,
            propertyValues = emptyList(),
          )

        val deleted =
          repository.softDelete(
            ink.doa.workbench.core.workitem.model.DeleteWorkItemCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              workItemApiId = created.workItem.apiId.value,
              actorUserId = stack.actorId,
            )
          )

        deleted.eventType shouldBe "work_item.updated"
        repository
          .findByApiId(stack.tenantId, stack.projectId, created.workItem.apiId.value)
          .shouldBeNull()
      }
    }

    "listByProject returns created issues" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val repository = ExposedWorkItemRepository(database)
        repository.create(
          CreateWorkItemCommand(
            tenantId = stack.tenantId,
            projectId = stack.projectId,
            issueTypeApiId = stack.issueType.apiId.value,
            title = "Listed issue",
            description = null,
            reporterId = stack.actorId,
            actorUserId = stack.actorId,
          ),
          issueTypeId = stack.issueType.id,
          issueTypeConfigId = stack.config.config.id,
          initialStatusId = stack.todoStatus.id,
          propertyValues = emptyList(),
        )

        repository.listByProject(stack.tenantId, stack.projectId).single().title shouldBe
          "Listed issue"
      }
    }

    "create persists custom property values" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val points =
          catalog.createProperty(
            CreatePropertyDefinitionCommand(
              tenantId = stack.tenantId,
              code = "points",
              name = "Points",
              description = null,
              dataType = WorkItemPropertyDataType.NUMBER,
            )
          )
        val notes =
          catalog.createProperty(
            CreatePropertyDefinitionCommand(
              tenantId = stack.tenantId,
              code = "notes",
              name = "Notes",
              description = null,
              dataType = WorkItemPropertyDataType.TEXT,
            )
          )
        val repository = ExposedWorkItemRepository(database)
        val propertyValues =
          listOf(
            WorkItemPropertyValue(
              propertyId = points.id,
              propertyApiId = points.apiId,
              code = points.code,
              dataType = WorkItemPropertyDataType.NUMBER,
              value = JsonPrimitive("5"),
            ),
            WorkItemPropertyValue(
              propertyId = notes.id,
              propertyApiId = notes.apiId,
              code = notes.code,
              dataType = WorkItemPropertyDataType.TEXT,
              value = JsonPrimitive("important"),
            ),
          )

        val result =
          repository.create(
            CreateWorkItemCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              issueTypeApiId = stack.issueType.apiId.value,
              title = "With properties",
              description = null,
              reporterId = stack.actorId,
              actorUserId = stack.actorId,
            ),
            issueTypeId = stack.issueType.id,
            issueTypeConfigId = stack.config.config.id,
            initialStatusId = stack.todoStatus.id,
            propertyValues = propertyValues,
          )

        val loaded =
          repository
            .findByApiId(stack.tenantId, stack.projectId, result.workItem.apiId.value)
            .shouldNotBeNull()
        loaded.properties["points"] shouldBe JsonPrimitive("5")
        loaded.properties["notes"] shouldBe JsonPrimitive("important")
      }
    }
  })
