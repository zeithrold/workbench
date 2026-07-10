package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.workitem.model.CreateIssueSubtypeConstraintCommand
import ink.doa.workbench.core.workitem.model.CreateIssueTypeCommand
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.withPostgresDatabase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ExposedIssueSubtypeConstraintRepositoryIntegrationTest :
  StringSpec({
    "create list resolve and deactivate subtype constraints" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val repository = ExposedIssueSubtypeConstraintRepository(database)
        val childType =
          catalog.createIssueType(
            CreateIssueTypeCommand(
              tenantId = stack.tenantId,
              scope = WorkItemConfigScope.TENANT,
              code = "subtask",
              name = "Sub-task",
            )
          )

        val created =
          repository.create(
            CreateIssueSubtypeConstraintCommand(
              tenantId = stack.tenantId,
              parentIssueTypeApiId = stack.issueType.apiId.value,
              childIssueTypeApiId = childType.apiId.value,
              isDefault = true,
              createdBy = stack.actorId,
            )
          )

        created.parentIssueTypeApiId shouldBe stack.issueType.apiId
        created.childIssueTypeApiId shouldBe childType.apiId
        repository.list(stack.tenantId).single().id shouldBe created.id
        repository.isChildOnlyType(stack.tenantId, stack.projectId, childType.id) shouldBe true
        repository
          .findAllowedChildType(
            stack.tenantId,
            stack.projectId,
            stack.issueType.id,
            childType.id,
          )
          ?.id shouldBe created.id

        repository.deactivate(stack.tenantId, created.id, stack.actorId).isActive shouldBe false
        repository.list(stack.tenantId).size shouldBe 0
        repository.isChildOnlyType(stack.tenantId, stack.projectId, childType.id) shouldBe false
      }
    }

    "create rejects invalid cardinality bounds" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val catalog = ExposedWorkItemCatalogRepository(database)
        val repository = ExposedIssueSubtypeConstraintRepository(database)
        val childType =
          catalog.createIssueType(
            CreateIssueTypeCommand(
              tenantId = stack.tenantId,
              scope = WorkItemConfigScope.TENANT,
              code = "subtask",
              name = "Sub-task",
            )
          )

        shouldThrow<InvalidRequestException> {
          repository.create(
            CreateIssueSubtypeConstraintCommand(
              tenantId = stack.tenantId,
              parentIssueTypeApiId = stack.issueType.apiId.value,
              childIssueTypeApiId = childType.apiId.value,
              minChildren = 3,
              maxChildren = 1,
            )
          )
        }
      }
    }
  })
