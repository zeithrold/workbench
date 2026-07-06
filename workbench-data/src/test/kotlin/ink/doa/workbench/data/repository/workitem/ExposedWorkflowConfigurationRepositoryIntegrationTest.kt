package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.CreateIssueStatusCommand
import ink.doa.workbench.core.workitem.model.CreateWorkflowCommand
import ink.doa.workbench.core.workitem.model.CreateWorkflowTransitionCommand
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.data.persistence.postgres.identity.TenantsTable
import ink.doa.workbench.data.support.seedUser
import ink.doa.workbench.data.support.withPostgresDatabase
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Tags("integration")
class ExposedWorkflowConfigurationRepositoryIntegrationTest :
  StringSpec({
    "createWorkflow persists and listWorkflows returns it" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val repository = ExposedWorkflowConfigurationRepository(database, catalog)

        val created =
          repository.createWorkflow(
            CreateWorkflowCommand(
              tenantId = tenantId,
              code = "default",
              name = "Default Workflow",
              description = "Primary workflow",
            )
          )

        val workflows = repository.listWorkflows(tenantId)
        workflows.shouldHaveSize(1)
        workflows.single().code shouldBe "default"
        workflows.single().id shouldBe created.id
      }
    }

    "findWorkflow resolves by code" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val repository = ExposedWorkflowConfigurationRepository(database, catalog)
        repository.createWorkflow(
          CreateWorkflowCommand(
            tenantId = tenantId,
            code = "support",
            name = "Support Workflow",
          )
        )

        repository.findWorkflow(tenantId, "support")?.name shouldBe "Support Workflow"
      }
    }

    "publishWorkflow sets publishedAt" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val repository = ExposedWorkflowConfigurationRepository(database, catalog)
        val workflow =
          repository.createWorkflow(
            CreateWorkflowCommand(tenantId = tenantId, code = "default", name = "Default")
          )
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val published = repository.publishWorkflow(tenantId, workflow.id, now)
        published.publishedAt.shouldNotBeNull()
        published.publishedAt!!.toInstant().epochSecond shouldBe now.toInstant().epochSecond
      }
    }

    "createTransition persists transition between statuses" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val repository = ExposedWorkflowConfigurationRepository(database, catalog)
        val workflow =
          repository.createWorkflow(
            CreateWorkflowCommand(tenantId = tenantId, code = "default", name = "Default")
          )
        val todo =
          catalog.createStatus(
            CreateIssueStatusCommand(
              tenantId = tenantId,
              code = "todo",
              name = "To Do",
              statusGroup = WorkItemStatusGroup.TODO,
            )
          )
        val done =
          catalog.createStatus(
            CreateIssueStatusCommand(
              tenantId = tenantId,
              code = "done",
              name = "Done",
              statusGroup = WorkItemStatusGroup.DONE,
              isTerminal = true,
            )
          )

        val transition =
          repository.createTransition(
            CreateWorkflowTransitionCommand(
              tenantId = tenantId,
              workflowApiId = workflow.apiId.value,
              name = "Complete",
              fromStatusApiId = todo.apiId.value,
              toStatusApiId = done.apiId.value,
            )
          )

        repository.listTransitions(tenantId, workflow.id).single().id shouldBe transition.id
      }
    }

    "deactivateWorkflow marks workflow inactive" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val actorId = seedUser(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val repository = ExposedWorkflowConfigurationRepository(database, catalog)
        val workflow =
          repository.createWorkflow(
            CreateWorkflowCommand(tenantId = tenantId, code = "legacy", name = "Legacy")
          )

        repository.deactivateWorkflow(tenantId, workflow.apiId.value, actorId).isActive shouldBe
          false
      }
    }

    "deactivateTransition marks transition inactive" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val repository = ExposedWorkflowConfigurationRepository(database, catalog)
        val workflow =
          repository.createWorkflow(
            CreateWorkflowCommand(tenantId = tenantId, code = "default", name = "Default")
          )
        val todo =
          catalog.createStatus(
            CreateIssueStatusCommand(
              tenantId = tenantId,
              code = "todo",
              name = "To Do",
              statusGroup = WorkItemStatusGroup.TODO,
            )
          )
        val done =
          catalog.createStatus(
            CreateIssueStatusCommand(
              tenantId = tenantId,
              code = "done",
              name = "Done",
              statusGroup = WorkItemStatusGroup.DONE,
              isTerminal = true,
            )
          )
        val transition =
          repository.createTransition(
            CreateWorkflowTransitionCommand(
              tenantId = tenantId,
              workflowApiId = workflow.apiId.value,
              name = "Complete",
              fromStatusApiId = todo.apiId.value,
              toStatusApiId = done.apiId.value,
            )
          )

        repository.deactivateTransition(tenantId, transition.apiId.value).isActive shouldBe false
        repository.findTransition(tenantId, transition.apiId.value).shouldBeNull()
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
