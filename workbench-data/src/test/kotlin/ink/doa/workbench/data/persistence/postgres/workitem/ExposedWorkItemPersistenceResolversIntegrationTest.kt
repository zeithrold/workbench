package ink.doa.workbench.data.persistence.postgres.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.CreateWorkItemPersistenceCommand
import ink.doa.workbench.core.workitem.model.CreatePropertyDefinitionCommand
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.data.repository.identity.ExposedUserRepository
import ink.doa.workbench.data.repository.project.ExposedProjectRepository
import ink.doa.workbench.data.repository.workitem.ExposedWorkItemCatalogRepository
import ink.doa.workbench.data.repository.workitem.ExposedWorkItemRepository
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.withPostgresDatabase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Tag

@Tag("integration")
class ExposedWorkItemPersistenceResolversIntegrationTest :
  StringSpec({
    "resolve helpers map public ids to database ids" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val users = ExposedUserRepository(database)
        val projects = ExposedProjectRepository(database)
        val repository = ExposedWorkItemRepository(database)
        val user = users.findById(stack.actorId).shouldNotBeNull()
        val project = projects.findById(stack.tenantId, stack.projectId).shouldNotBeNull()
        val created =
          repository.create(
            CreateWorkItemPersistenceCommand(
              command =
                CreateWorkItemCommand(
                  tenantId = stack.tenantId,
                  projectId = stack.projectId,
                  issueTypeApiId = stack.issueType.apiId.value,
                  title = "Resolver target",
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

        transaction(database) {
          resolveUser(user.apiId.value) shouldBe stack.actorId
          resolveProject(stack.tenantId, project.apiId.value) shouldBe stack.projectId
          resolveIssue(stack.tenantId, created.workItem.apiId.value) shouldBe created.workItem.id

          val now = OffsetDateTime.now(ZoneOffset.UTC)
          val priorityId = UUID.randomUUID()
          val sprintId = UUID.randomUUID()
          val sprintApiId = PublicId.new("spr").value
          PrioritiesTable.insert {
            it[id] = priorityId.toKotlinUuid()
            it[apiId] = PublicId.new("pri").value
            it[tenantId] = stack.tenantId.toKotlinUuid()
            it[code] = "high"
            it[name] = "High"
            it[rank] = 10
            it[isDefault] = false
            it[isActive] = true
            it[createdAt] = now
            it[updatedAt] = now
          }
          SprintsTable.insert {
            it[id] = sprintId.toKotlinUuid()
            it[apiId] = sprintApiId
            it[tenantId] = stack.tenantId.toKotlinUuid()
            it[projectId] = stack.projectId.toKotlinUuid()
            it[name] = "Sprint 1"
            it[status] = "active"
            it[createdAt] = now
            it[updatedAt] = now
          }
          resolvePriority(stack.tenantId, "high") shouldBe priorityId
          resolveSprint(stack.tenantId, stack.projectId, sprintApiId) shouldBe sprintId
        }
      }
    }

    "resolve helpers throw when references are missing" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)

        transaction(database) {
          shouldThrow<ResourceNotFoundException> { resolveUser("usr_missing") }
          shouldThrow<ResourceNotFoundException> {
            resolveProject(stack.tenantId, "prj_missing")
          }
          shouldThrow<ResourceNotFoundException> {
            resolveIssue(stack.tenantId, "iss_missing")
          }
          shouldThrow<ResourceNotFoundException> {
            resolvePriority(stack.tenantId, "pri_missing")
          }
          shouldThrow<ResourceNotFoundException> {
            resolveSprint(stack.tenantId, stack.projectId, "spr_missing")
          }
        }
      }
    }

    "resolveOption maps option api id to database id" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val property =
          catalog.createProperty(
            CreatePropertyDefinitionCommand(
              tenantId = stack.tenantId,
              code = "severity",
              name = "Severity",
              description = null,
              dataType = WorkItemPropertyDataType.SINGLE_SELECT,
            )
          )
        val optionApiId = PublicId.new("opt").value
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        transaction(database) {
          ink.doa.workbench.data.persistence.postgres.workitem.PropertyOptionsTable.insert {
            it[id] = UUID.randomUUID().toKotlinUuid()
            it[apiId] = optionApiId
            it[tenantId] = stack.tenantId.toKotlinUuid()
            it[propertyId] = property.id.toKotlinUuid()
            it[code] = "high"
            it[label] = "High"
            it[rank] = 1
            it[isDefault] = false
            it[isActive] = true
            it[createdAt] = now
            it[updatedAt] = now
          }

          resolveOption(property.id, optionApiId)
          resolveOption(property.id, "high")
        }
      }
    }

    "writePropertyColumns persists user and project typed values" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val projects = ExposedProjectRepository(database)
        val users = ExposedUserRepository(database)
        val repository = ExposedWorkItemRepository(database)
        val user = users.findById(stack.actorId).shouldNotBeNull()
        val project = projects.findById(stack.tenantId, stack.projectId).shouldNotBeNull()
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
        val propertyValues =
          listOf(
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
          )

        val created =
          repository.create(
            CreateWorkItemPersistenceCommand(
              command =
                CreateWorkItemCommand(
                  tenantId = stack.tenantId,
                  projectId = stack.projectId,
                  issueTypeApiId = stack.issueType.apiId.value,
                  title = "Reference properties",
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
        loaded.properties["owner"] shouldBe JsonPrimitive(user.apiId.value)
        loaded.properties["relatedProject"] shouldBe JsonPrimitive(project.apiId.value)
      }
    }
  })
