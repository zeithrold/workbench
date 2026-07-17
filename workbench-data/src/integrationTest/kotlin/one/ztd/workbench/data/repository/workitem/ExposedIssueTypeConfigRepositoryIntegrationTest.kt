package one.ztd.workbench.data.repository.workitem

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import one.ztd.workbench.agile.workitem.model.CreateIssueTypeConfigCommand
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigPropertyInput
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigStatusInput
import one.ztd.workbench.agile.workitem.model.WorkItemConfigScope
import one.ztd.workbench.data.support.seedWorkItemStack
import one.ztd.workbench.data.support.withPostgresDatabase
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException

class ExposedIssueTypeConfigRepositoryIntegrationTest :
  StringSpec({
    "createConfig persists statuses and listConfigs returns it" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val workflows = ExposedWorkflowConfigurationRepository(database, catalog)
        val repository = ExposedIssueTypeConfigRepository(database, catalog, workflows)

        val configs = repository.listConfigs(stack.tenantId, null)
        configs.shouldHaveSize(1)
        configs.single().config.issueTypeApiId shouldBe stack.issueType.apiId
        configs.single().statuses.map { it.statusApiId } shouldBe
          listOf(stack.todoStatus.apiId, stack.doneStatus.apiId)
      }
    }

    "findConfig resolves by api id" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)

        ExposedIssueTypeConfigRepository(
            database,
            ExposedWorkItemCatalogRepository(database),
            ExposedWorkflowConfigurationRepository(
              database,
              ExposedWorkItemCatalogRepository(database),
            ),
          )
          .findConfig(stack.tenantId, stack.config.config.apiId.value)
          ?.config
          ?.workflowApiId shouldBe stack.workflow.apiId
      }
    }

    "resolveEffective prefers project scope config" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val workflows = ExposedWorkflowConfigurationRepository(database, catalog)
        val repository = ExposedIssueTypeConfigRepository(database, catalog, workflows)
        repository.createConfig(
          CreateIssueTypeConfigCommand(
            tenantId = stack.tenantId,
            scope = WorkItemConfigScope.PROJECT,
            projectId = stack.projectId,
            issueTypeApiId = stack.issueType.apiId.value,
            workflowApiId = stack.workflow.apiId.value,
            createdBy = stack.actorId,
            createFields = JsonObject(emptyMap()),
            statuses =
              listOf(
                IssueTypeConfigStatusInput(
                  statusApiId = stack.todoStatus.apiId.value,
                  isInitial = true,
                )
              ),
          )
        )

        val effective =
          repository.resolveEffective(
            stack.tenantId,
            stack.projectId,
            stack.issueType.apiId.value,
          )

        effective?.resolvedFrom shouldBe WorkItemConfigScope.PROJECT
      }
    }

    "findConfig returns null for unknown api id" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val workflows = ExposedWorkflowConfigurationRepository(database, catalog)
        val repository = ExposedIssueTypeConfigRepository(database, catalog, workflows)

        repository.findConfig(stack.tenantId, "itc_missing").shouldBeNull()
      }
    }

    "listConfigs with project id includes project scoped config" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val workflows = ExposedWorkflowConfigurationRepository(database, catalog)
        val repository = ExposedIssueTypeConfigRepository(database, catalog, workflows)
        repository.createConfig(
          CreateIssueTypeConfigCommand(
            tenantId = stack.tenantId,
            scope = WorkItemConfigScope.PROJECT,
            projectId = stack.projectId,
            issueTypeApiId = stack.issueType.apiId.value,
            workflowApiId = stack.workflow.apiId.value,
            createdBy = stack.actorId,
            createFields = JsonObject(emptyMap()),
            statuses =
              listOf(
                IssueTypeConfigStatusInput(
                  statusApiId = stack.todoStatus.apiId.value,
                  isInitial = true,
                )
              ),
          )
        )

        repository.listConfigs(stack.tenantId, stack.projectId).shouldHaveSize(2)
      }
    }

    "createConfig persists configured properties" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val workflows = ExposedWorkflowConfigurationRepository(database, catalog)
        val repository = ExposedIssueTypeConfigRepository(database, catalog, workflows)
        val property =
          catalog.createProperty(
            one.ztd.workbench.agile.workitem.model.CreatePropertyDefinitionCommand(
              tenantId = stack.tenantId,
              code = "labels",
              name = "Labels",
              description = null,
              dataType = one.ztd.workbench.agile.workitem.model.WorkItemPropertyDataType.TEXT,
            )
          )
        val created =
          repository.createConfig(
            CreateIssueTypeConfigCommand(
              tenantId = stack.tenantId,
              scope = WorkItemConfigScope.TENANT,
              projectId = null,
              issueTypeApiId = stack.issueType.apiId.value,
              workflowApiId = stack.workflow.apiId.value,
              createdBy = stack.actorId,
              createFields = JsonObject(emptyMap()),
              statuses =
                listOf(
                  IssueTypeConfigStatusInput(
                    statusApiId = stack.todoStatus.apiId.value,
                    isInitial = true,
                  )
                ),
              properties = listOf(IssueTypeConfigPropertyInput(property.apiId.value)),
            )
          )

        created.properties.single().code shouldBe "labels"
        repository
          .findConfig(stack.tenantId, created.config.apiId.value)
          ?.properties
          ?.single()
          ?.code shouldBe "labels"
      }
    }

    "createConfig rejects missing issue type" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val workflows = ExposedWorkflowConfigurationRepository(database, catalog)
        val repository = ExposedIssueTypeConfigRepository(database, catalog, workflows)

        shouldThrow<ResourceNotFoundException> {
          repository.createConfig(
            CreateIssueTypeConfigCommand(
              tenantId = stack.tenantId,
              scope = WorkItemConfigScope.TENANT,
              projectId = null,
              issueTypeApiId = "typ_missing",
              workflowApiId = stack.workflow.apiId.value,
              createdBy = stack.actorId,
              createFields = JsonObject(emptyMap()),
              statuses =
                listOf(
                  IssueTypeConfigStatusInput(
                    statusApiId = stack.todoStatus.apiId.value,
                    isInitial = true,
                  )
                ),
            )
          )
        }
      }
    }

    "createConfig rejects missing workflow" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val workflows = ExposedWorkflowConfigurationRepository(database, catalog)
        val repository = ExposedIssueTypeConfigRepository(database, catalog, workflows)

        shouldThrow<ResourceNotFoundException> {
          repository.createConfig(
            CreateIssueTypeConfigCommand(
              tenantId = stack.tenantId,
              scope = WorkItemConfigScope.TENANT,
              projectId = null,
              issueTypeApiId = stack.issueType.apiId.value,
              workflowApiId = "wfl_missing",
              createdBy = stack.actorId,
              createFields = JsonObject(emptyMap()),
              statuses =
                listOf(
                  IssueTypeConfigStatusInput(
                    statusApiId = stack.todoStatus.apiId.value,
                    isInitial = true,
                  )
                ),
            )
          )
        }
      }
    }
  })
