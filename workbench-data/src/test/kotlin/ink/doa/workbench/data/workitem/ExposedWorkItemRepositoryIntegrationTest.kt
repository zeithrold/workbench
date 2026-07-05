package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.CreateWorkItemPersistenceCommand
import ink.doa.workbench.core.workitem.model.CreatePropertyDefinitionCommand
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.CreateWorkflowTransitionCommand
import ink.doa.workbench.core.workitem.model.TransitionWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.data.persistence.PrioritiesTable
import ink.doa.workbench.data.persistence.PropertyOptionsTable
import ink.doa.workbench.data.persistence.SprintsTable
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.withPostgresDatabase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

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
            CreateWorkItemPersistenceCommand(
              command =
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
          )

        result.workItem.title shouldBe "First issue"
        result.workItem.statusApiId shouldBe stack.todoStatus.apiId
        result.eventType shouldBe "work_item.created"
        repository
          .findByApiId(stack.tenantId, stack.projectId, result.workItem.apiId.value)
          .shouldNotBeNull()
      }
    }

    "create with parent persists hierarchy link" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val repository = ExposedWorkItemRepository(database)
        val parent =
          repository.create(
            CreateWorkItemPersistenceCommand(
              command =
                CreateWorkItemCommand(
                  tenantId = stack.tenantId,
                  projectId = stack.projectId,
                  issueTypeApiId = stack.issueType.apiId.value,
                  title = "Parent issue",
                  description = null,
                  reporterId = stack.actorId,
                  actorUserId = stack.actorId,
                ),
              issueTypeId = stack.issueType.id,
              issueTypeConfigId = stack.config.config.id,
              initialStatusId = stack.todoStatus.id,
              propertyValues = emptyList(),
            )
          )

        val child =
          repository.create(
            CreateWorkItemPersistenceCommand(
              command =
                CreateWorkItemCommand(
                  tenantId = stack.tenantId,
                  projectId = stack.projectId,
                  issueTypeApiId = stack.issueType.apiId.value,
                  title = "Child issue",
                  description = null,
                  reporterId = stack.actorId,
                  actorUserId = stack.actorId,
                ),
              issueTypeId = stack.issueType.id,
              issueTypeConfigId = stack.config.config.id,
              initialStatusId = stack.todoStatus.id,
              propertyValues = emptyList(),
              parentIssueId = parent.workItem.id,
            )
          )

        child.workItem.title shouldBe "Child issue"
        repository.countChildrenNotInStatusGroups(
          stack.tenantId,
          parent.workItem.id,
          setOf("done"),
        ) shouldBe 1
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
            CreateWorkItemPersistenceCommand(
              command =
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
            CreateWorkItemPersistenceCommand(
              command =
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
            CreateWorkItemPersistenceCommand(
              command =
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
          CreateWorkItemPersistenceCommand(
            command =
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
            CreateWorkItemPersistenceCommand(
              command =
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
          )

        val loaded =
          repository
            .findByApiId(stack.tenantId, stack.projectId, result.workItem.apiId.value)
            .shouldNotBeNull()
        loaded.properties["points"] shouldBe JsonPrimitive("5")
        loaded.properties["notes"] shouldBe JsonPrimitive("important")
      }
    }

    "create persists priority assignee and sprint references" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val users = ink.doa.workbench.data.identity.ExposedUserRepository(database)
        val assignee = users.findById(stack.actorId).shouldNotBeNull()
        val priorityApiId = PublicId.new("pri").value
        val sprintApiId = PublicId.new("spr").value
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        transaction(database) {
          PrioritiesTable.insert {
            it[id] = UUID.randomUUID().toKotlinUuid()
            it[apiId] = priorityApiId
            it[tenantId] = stack.tenantId.toKotlinUuid()
            it[code] = "medium"
            it[name] = "Medium"
            it[rank] = 5
            it[isDefault] = true
            it[isActive] = true
            it[createdAt] = now
            it[updatedAt] = now
          }
          SprintsTable.insert {
            it[id] = UUID.randomUUID().toKotlinUuid()
            it[apiId] = sprintApiId
            it[tenantId] = stack.tenantId.toKotlinUuid()
            it[projectId] = stack.projectId.toKotlinUuid()
            it[name] = "Sprint A"
            it[status] = "active"
            it[createdAt] = now
            it[updatedAt] = now
          }
        }
        val repository = ExposedWorkItemRepository(database)
        val created =
          repository.create(
            CreateWorkItemPersistenceCommand(
              command =
                CreateWorkItemCommand(
                  tenantId = stack.tenantId,
                  projectId = stack.projectId,
                  issueTypeApiId = stack.issueType.apiId.value,
                  title = "Assigned issue",
                  description = "Body",
                  reporterId = stack.actorId,
                  actorUserId = stack.actorId,
                  assigneeApiId = assignee.apiId.value,
                  priorityApiId = priorityApiId,
                  sprintApiId = sprintApiId,
                ),
              issueTypeId = stack.issueType.id,
              issueTypeConfigId = stack.config.config.id,
              initialStatusId = stack.todoStatus.id,
              propertyValues = emptyList(),
            )
          )

        val loaded =
          repository
            .findByApiId(stack.tenantId, stack.projectId, created.workItem.apiId.value)
            .shouldNotBeNull()
        loaded.assigneeApiId shouldBe assignee.apiId
        loaded.priorityApiId?.value shouldBe priorityApiId
        loaded.sprintApiId?.value shouldBe sprintApiId
        loaded.description shouldBe "Body"
      }
    }

    "update replaces scalar fields and property values" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val users = ink.doa.workbench.data.identity.ExposedUserRepository(database)
        val assignee = users.findById(stack.actorId).shouldNotBeNull()
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
        val priorityApiId = PublicId.new("pri").value
        val sprintApiId = PublicId.new("spr").value
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        transaction(database) {
          PrioritiesTable.insert {
            it[id] = UUID.randomUUID().toKotlinUuid()
            it[apiId] = priorityApiId
            it[tenantId] = stack.tenantId.toKotlinUuid()
            it[code] = "low"
            it[name] = "Low"
            it[rank] = 1
            it[isDefault] = false
            it[isActive] = true
            it[createdAt] = now
            it[updatedAt] = now
          }
          SprintsTable.insert {
            it[id] = UUID.randomUUID().toKotlinUuid()
            it[apiId] = sprintApiId
            it[tenantId] = stack.tenantId.toKotlinUuid()
            it[projectId] = stack.projectId.toKotlinUuid()
            it[name] = "Sprint B"
            it[status] = "active"
            it[createdAt] = now
            it[updatedAt] = now
          }
        }
        val repository = ExposedWorkItemRepository(database)
        val created =
          repository.create(
            CreateWorkItemPersistenceCommand(
              command =
                CreateWorkItemCommand(
                  tenantId = stack.tenantId,
                  projectId = stack.projectId,
                  issueTypeApiId = stack.issueType.apiId.value,
                  title = "Before",
                  description = "Old",
                  reporterId = stack.actorId,
                  actorUserId = stack.actorId,
                ),
              issueTypeId = stack.issueType.id,
              issueTypeConfigId = stack.config.config.id,
              initialStatusId = stack.todoStatus.id,
              propertyValues =
                listOf(
                  WorkItemPropertyValue(
                    propertyId = points.id,
                    propertyApiId = points.apiId,
                    code = points.code,
                    dataType = WorkItemPropertyDataType.NUMBER,
                    value = JsonPrimitive("3"),
                  )
                ),
            )
          )
        val updatedProperty =
          WorkItemPropertyValue(
            propertyId = points.id,
            propertyApiId = points.apiId,
            code = points.code,
            dataType = WorkItemPropertyDataType.NUMBER,
            value = JsonPrimitive("8"),
          )

        val updated =
          repository.update(
            ink.doa.workbench.core.workitem.model.UpdateWorkItemCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              workItemApiId = created.workItem.apiId.value,
              title = "After",
              description = "New body",
              descriptionPlainText = "New body",
              assigneeApiId = assignee.apiId.value,
              priorityApiId = priorityApiId,
              sprintApiId = sprintApiId,
              actorUserId = stack.actorId,
            ),
            propertyValues = listOf(updatedProperty),
          )

        updated.workItem.title shouldBe "After"
        updated.workItem.description shouldBe "New body"
        updated.workItem.assigneeApiId shouldBe assignee.apiId
        updated.workItem.priorityApiId?.value shouldBe priorityApiId
        updated.workItem.sprintApiId?.value shouldBe sprintApiId
        (repository.listPropertyValues(stack.tenantId, created.workItem.id)["points"]
            as JsonPrimitive)
          .content
          .toDouble() shouldBe 8.0
      }
    }

    "listPropertyValues maps persisted columns to json values" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val users = ink.doa.workbench.data.identity.ExposedUserRepository(database)
        val projects = ink.doa.workbench.data.project.ExposedProjectRepository(database)
        val user = users.findById(stack.actorId).shouldNotBeNull()
        val project = projects.findById(stack.tenantId, stack.projectId).shouldNotBeNull()
        val activeProperty =
          catalog.createProperty(
            CreatePropertyDefinitionCommand(
              tenantId = stack.tenantId,
              code = "active",
              name = "Active",
              description = null,
              dataType = WorkItemPropertyDataType.BOOLEAN,
            )
          )
        val reviewedAt =
          catalog.createProperty(
            CreatePropertyDefinitionCommand(
              tenantId = stack.tenantId,
              code = "reviewedAt",
              name = "Reviewed At",
              description = null,
              dataType = WorkItemPropertyDataType.DATETIME,
            )
          )
        val metadata =
          catalog.createProperty(
            CreatePropertyDefinitionCommand(
              tenantId = stack.tenantId,
              code = "metadata",
              name = "Metadata",
              description = null,
              dataType = WorkItemPropertyDataType.JSON,
            )
          )
        val severity =
          catalog.createProperty(
            CreatePropertyDefinitionCommand(
              tenantId = stack.tenantId,
              code = "severity",
              name = "Severity",
              description = null,
              dataType = WorkItemPropertyDataType.SINGLE_SELECT,
            )
          )
        val tags =
          catalog.createProperty(
            CreatePropertyDefinitionCommand(
              tenantId = stack.tenantId,
              code = "tags",
              name = "Tags",
              description = null,
              dataType = WorkItemPropertyDataType.MULTI_SELECT,
            )
          )
        val ownerProperty =
          catalog.createProperty(
            CreatePropertyDefinitionCommand(
              tenantId = stack.tenantId,
              code = "owner",
              name = "Owner",
              description = null,
              dataType = WorkItemPropertyDataType.USER,
            )
          )
        val relatedProjectProperty =
          catalog.createProperty(
            CreatePropertyDefinitionCommand(
              tenantId = stack.tenantId,
              code = "relatedProject",
              name = "Related Project",
              description = null,
              dataType = WorkItemPropertyDataType.PROJECT,
            )
          )
        val relatedIssue =
          catalog.createProperty(
            CreatePropertyDefinitionCommand(
              tenantId = stack.tenantId,
              code = "relatedIssue",
              name = "Related Issue",
              description = null,
              dataType = WorkItemPropertyDataType.ISSUE,
            )
          )
        val optionApiId = PublicId.new("opt").value
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        transaction(database) {
          PropertyOptionsTable.insert {
            it[id] = UUID.randomUUID().toKotlinUuid()
            it[apiId] = optionApiId
            it[tenantId] = stack.tenantId.toKotlinUuid()
            it[propertyId] = severity.id.toKotlinUuid()
            it[code] = "critical"
            it[label] = "Critical"
            it[rank] = 1
            it[isDefault] = false
            it[isActive] = true
            it[createdAt] = now
            it[updatedAt] = now
          }
        }
        val repository = ExposedWorkItemRepository(database)
        val blocker =
          repository.create(
            CreateWorkItemPersistenceCommand(
              command =
                CreateWorkItemCommand(
                  tenantId = stack.tenantId,
                  projectId = stack.projectId,
                  issueTypeApiId = stack.issueType.apiId.value,
                  title = "Blocker",
                  description = null,
                  reporterId = stack.actorId,
                  actorUserId = stack.actorId,
                ),
              issueTypeId = stack.issueType.id,
              issueTypeConfigId = stack.config.config.id,
              initialStatusId = stack.todoStatus.id,
              propertyValues = emptyList(),
            )
          )
        val typedValues =
          listOf(
            WorkItemPropertyValue(
              propertyId = activeProperty.id,
              propertyApiId = activeProperty.apiId,
              code = activeProperty.code,
              dataType = WorkItemPropertyDataType.BOOLEAN,
              value = JsonPrimitive(true),
            ),
            WorkItemPropertyValue(
              propertyId = reviewedAt.id,
              propertyApiId = reviewedAt.apiId,
              code = reviewedAt.code,
              dataType = WorkItemPropertyDataType.DATETIME,
              value = JsonPrimitive("2024-06-01T12:00:00Z"),
            ),
            WorkItemPropertyValue(
              propertyId = metadata.id,
              propertyApiId = metadata.apiId,
              code = metadata.code,
              dataType = WorkItemPropertyDataType.JSON,
              value = JsonObject(mapOf("tier" to JsonPrimitive("gold"))),
            ),
            WorkItemPropertyValue(
              propertyId = severity.id,
              propertyApiId = severity.apiId,
              code = severity.code,
              dataType = WorkItemPropertyDataType.SINGLE_SELECT,
              value = JsonPrimitive(optionApiId),
            ),
            WorkItemPropertyValue(
              propertyId = tags.id,
              propertyApiId = tags.apiId,
              code = tags.code,
              dataType = WorkItemPropertyDataType.MULTI_SELECT,
              value = JsonArray(listOf(JsonPrimitive("backend"), JsonPrimitive("urgent"))),
            ),
            WorkItemPropertyValue(
              propertyId = ownerProperty.id,
              propertyApiId = ownerProperty.apiId,
              code = ownerProperty.code,
              dataType = WorkItemPropertyDataType.USER,
              value = JsonPrimitive(user.apiId.value),
            ),
            WorkItemPropertyValue(
              propertyId = relatedProjectProperty.id,
              propertyApiId = relatedProjectProperty.apiId,
              code = relatedProjectProperty.code,
              dataType = WorkItemPropertyDataType.PROJECT,
              value = JsonPrimitive(project.apiId.value),
            ),
            WorkItemPropertyValue(
              propertyId = relatedIssue.id,
              propertyApiId = relatedIssue.apiId,
              code = relatedIssue.code,
              dataType = WorkItemPropertyDataType.ISSUE,
              value = JsonPrimitive(blocker.workItem.apiId.value),
            ),
          )
        val created =
          repository.create(
            CreateWorkItemPersistenceCommand(
              command =
                CreateWorkItemCommand(
                  tenantId = stack.tenantId,
                  projectId = stack.projectId,
                  issueTypeApiId = stack.issueType.apiId.value,
                  title = "Typed properties",
                  description = null,
                  reporterId = stack.actorId,
                  actorUserId = stack.actorId,
                ),
              issueTypeId = stack.issueType.id,
              issueTypeConfigId = stack.config.config.id,
              initialStatusId = stack.todoStatus.id,
              propertyValues = typedValues,
            )
          )

        val values = repository.listPropertyValues(stack.tenantId, created.workItem.id)
        values["active"] shouldBe JsonPrimitive(true)
        (values["reviewedAt"] as JsonPrimitive).content shouldContain "2024-06-01T12:00"
        values["metadata"] shouldBe JsonObject(mapOf("tier" to JsonPrimitive("gold")))
        values["severity"] shouldBe JsonPrimitive(optionApiId)
        values["tags"] shouldBe JsonArray(listOf(JsonPrimitive("backend"), JsonPrimitive("urgent")))
        values["owner"] shouldBe JsonPrimitive(user.apiId.value)
        values["relatedProject"] shouldBe JsonPrimitive(project.apiId.value)
        values["relatedIssue"] shouldBe JsonPrimitive(blocker.workItem.apiId.value)
      }
    }
  })
