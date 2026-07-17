package one.ztd.workbench.data.persistence.postgres.workitem

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.agile.workitem.CreateWorkItemPersistenceCommand
import one.ztd.workbench.agile.workitem.model.CreatePropertyDefinitionCommand
import one.ztd.workbench.agile.workitem.model.CreateWorkItemCommand
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyDataType
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyValue
import one.ztd.workbench.data.repository.workitem.ExposedWorkItemCatalogRepository
import one.ztd.workbench.data.support.seedWorkItemStack
import one.ztd.workbench.data.support.withPostgresDatabase
import one.ztd.workbench.data.support.workItemRepository

class WorkItemPropertyPersistenceIntegrationTest :
  StringSpec({
    "property persistence round-trips boolean datetime and json values" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val active =
          catalog.createProperty(
            CreatePropertyDefinitionCommand(
              tenantId = stack.tenantId,
              code = "active",
              name = "Active",
              description = null,
              dataType = WorkItemPropertyDataType.BOOLEAN,
            )
          )
        val dueAt =
          catalog.createProperty(
            CreatePropertyDefinitionCommand(
              tenantId = stack.tenantId,
              code = "due_at",
              name = "Due At",
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
        val repository = workItemRepository(database)
        val propertyValues =
          listOf(
            WorkItemPropertyValue(
              propertyId = active.id,
              propertyApiId = active.apiId,
              code = active.code,
              dataType = WorkItemPropertyDataType.BOOLEAN,
              value = JsonPrimitive("true"),
            ),
            WorkItemPropertyValue(
              propertyId = dueAt.id,
              propertyApiId = dueAt.apiId,
              code = dueAt.code,
              dataType = WorkItemPropertyDataType.DATETIME,
              value = JsonPrimitive("2026-07-04T12:00:00Z"),
            ),
            WorkItemPropertyValue(
              propertyId = metadata.id,
              propertyApiId = metadata.apiId,
              code = metadata.code,
              dataType = WorkItemPropertyDataType.JSON,
              value = JsonObject(mapOf("tier" to JsonPrimitive("gold"))),
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
              propertyValues = propertyValues,
            )
          )

        val loaded =
          repository
            .findByApiId(stack.tenantId, stack.projectId, created.workItem.apiId.value)
            .shouldNotBeNull()
        loaded.properties["active"] shouldBe JsonPrimitive("true")
        loaded.properties["due_at"].toString().shouldContain("2026-07-04T12:00:00")
        loaded.properties["metadata"] shouldBe JsonObject(mapOf("tier" to JsonPrimitive("gold")))
      }
    }
  })
