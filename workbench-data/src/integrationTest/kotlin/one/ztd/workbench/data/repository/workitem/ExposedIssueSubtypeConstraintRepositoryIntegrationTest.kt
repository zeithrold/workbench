package one.ztd.workbench.data.repository.workitem

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.agile.workitem.model.CreateIssueSubtypeConstraintCommand
import one.ztd.workbench.agile.workitem.model.CreateIssueTypeCommand
import one.ztd.workbench.agile.workitem.model.WorkItemConfigScope
import one.ztd.workbench.data.support.seedWorkItemStack
import one.ztd.workbench.data.support.withPostgresDatabase
import one.ztd.workbench.kernel.common.errors.InvalidRequestException

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
