package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.agile.workitem.model.CreateIssueStatusCommand
import ink.doa.workbench.agile.workitem.model.CreatePropertyDefinitionCommand
import ink.doa.workbench.agile.workitem.model.WorkItemPropertyDataType
import ink.doa.workbench.agile.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.data.persistence.postgres.identity.TenantsTable
import ink.doa.workbench.data.persistence.postgres.identity.UsersTable
import ink.doa.workbench.data.support.withPostgresDatabase
import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ExposedWorkItemCatalogRepositoryIntegrationTest :
  StringSpec({
    "createStatus persists and listStatuses returns it" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val repository = ExposedWorkItemCatalogRepository(database)

        val created =
          repository.createStatus(
            CreateIssueStatusCommand(
              tenantId = tenantId,
              code = "todo",
              name = "To Do",
              statusGroup = WorkItemStatusGroup.TODO,
              rank = 10,
            )
          )

        val statuses = repository.listStatuses(tenantId)
        statuses.shouldHaveSize(1)
        statuses.single().code shouldBe "todo"
        statuses.single().id shouldBe created.id
      }
    }

    "findStatus resolves by code" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val repository = ExposedWorkItemCatalogRepository(database)
        repository.createStatus(
          CreateIssueStatusCommand(
            tenantId = tenantId,
            code = "done",
            name = "Done",
            statusGroup = WorkItemStatusGroup.DONE,
            isTerminal = true,
          )
        )

        repository.findStatus(tenantId, "done")?.name shouldBe "Done"
      }
    }

    "createProperty persists and listProperties returns it" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val repository = ExposedWorkItemCatalogRepository(database)

        repository.createProperty(
          CreatePropertyDefinitionCommand(
            tenantId = tenantId,
            code = "storyPoints",
            name = "Story Points",
            description = "Estimate",
            dataType = WorkItemPropertyDataType.NUMBER,
          )
        )

        val properties = repository.listProperties(tenantId)
        properties.shouldHaveSize(1)
        properties.single().code shouldBe "storyPoints"
      }
    }

    "findProperty resolves by code" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val repository = ExposedWorkItemCatalogRepository(database)
        repository.createProperty(
          CreatePropertyDefinitionCommand(
            tenantId = tenantId,
            code = "severity",
            name = "Severity",
            description = null,
            dataType = WorkItemPropertyDataType.TEXT,
          )
        )

        repository.findProperty(tenantId, "severity")?.name shouldBe "Severity"
      }
    }

    "createIssueType persists and listIssueTypes returns it" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val repository = ExposedWorkItemCatalogRepository(database)

        repository.createIssueType(
          ink.doa.workbench.agile.workitem.model.CreateIssueTypeCommand(
            tenantId = tenantId,
            scope = ink.doa.workbench.agile.workitem.model.WorkItemConfigScope.TENANT,
            code = "bug",
            name = "Bug",
          )
        )

        repository.listIssueTypes(tenantId).single().code shouldBe "bug"
        repository.findIssueType(tenantId, "bug", null)?.name shouldBe "Bug"
      }
    }

    "deactivateStatus marks status inactive" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val actorId = seedUser(database)
        val repository = ExposedWorkItemCatalogRepository(database)
        val status =
          repository.createStatus(
            CreateIssueStatusCommand(
              tenantId = tenantId,
              code = "todo",
              name = "To Do",
              statusGroup = WorkItemStatusGroup.TODO,
            )
          )

        repository.deactivateStatus(tenantId, status.apiId.value, actorId).isActive shouldBe false
      }
    }

    "deactivateProperty marks property inactive" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val actorId = seedUser(database)
        val repository = ExposedWorkItemCatalogRepository(database)
        val property =
          repository.createProperty(
            CreatePropertyDefinitionCommand(
              tenantId = tenantId,
              code = "points",
              name = "Points",
              description = null,
              dataType = WorkItemPropertyDataType.NUMBER,
            )
          )

        repository.deactivateProperty(tenantId, property.apiId.value, actorId).isActive shouldBe
          false
      }
    }

    "deactivateIssueType marks issue type inactive" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val actorId = seedUser(database)
        val repository = ExposedWorkItemCatalogRepository(database)
        val issueType =
          repository.createIssueType(
            ink.doa.workbench.agile.workitem.model.CreateIssueTypeCommand(
              tenantId = tenantId,
              scope = ink.doa.workbench.agile.workitem.model.WorkItemConfigScope.TENANT,
              code = "task",
              name = "Task",
            )
          )

        repository.deactivateIssueType(tenantId, issueType.apiId.value, actorId).isActive shouldBe
          false
      }
    }
  })

private fun seedTenant(database: Database): UUID {
  val tenantId = UUID.randomUUID()
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  transaction(database) {
    TenantsTable.insert {
      it[id] = tenantId.toKotlinUuid()
      it[apiId] = PublicId.new("ten").value
      it[name] = "Test Tenant"
      it[slug] = "test-${tenantId.toString().take(8)}"
      it[timezone] = "UTC"
      it[locale] = "en-US"
      it[createdAt] = now
      it[updatedAt] = now
    }
  }
  return tenantId
}

private fun seedUser(database: Database): UUID {
  val userId = UUID.randomUUID()
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  transaction(database) {
    UsersTable.insert {
      it[id] = userId.toKotlinUuid()
      it[apiId] = PublicId.new("usr").value
      it[displayName] = "Ada"
      it[primaryEmail] = "ada-${userId.toString().take(8)}@example.test"
      it[createdAt] = now
      it[updatedAt] = now
    }
  }
  return userId
}
