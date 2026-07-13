package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.agile.workitem.access.CreateWorkItemAccessRuleCommand
import ink.doa.workbench.agile.workitem.access.WorkItemAccessActionType
import ink.doa.workbench.agile.workitem.access.WorkItemAccessSubjectType
import ink.doa.workbench.agile.workitem.model.CreateWorkflowTransitionCommand
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.withPostgresDatabase
import ink.doa.workbench.identity.permission.model.PermissionEffect
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ExposedWorkItemAccessRuleRepositoryIntegrationTest :
  StringSpec({
    "create list find and deactivate access rules" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val workflows =
          ExposedWorkflowConfigurationRepository(
            database,
            ExposedWorkItemCatalogRepository(database),
          )
        val repository = ExposedWorkItemAccessRuleRepository(database)
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

        val created =
          repository.create(
            CreateWorkItemAccessRuleCommand(
              tenantId = stack.tenantId,
              issueTypeConfigId = stack.config.config.id,
              subjectType = WorkItemAccessSubjectType.IN_ROLE,
              subjectRoleCode = "member",
              actionType = WorkItemAccessActionType.TRANSITION,
              transitionId = transition.id,
              effect = PermissionEffect.DENY,
              condition =
                JsonObject(
                  mapOf(
                    "op" to JsonPrimitive("eq"),
                    "field" to JsonPrimitive("statusGroup"),
                    "value" to JsonPrimitive("todo"),
                  )
                ),
              rank = 50,
            )
          )

        created.issueTypeConfigId shouldBe stack.config.config.id
        created.subjectRoleCode shouldBe "member"
        created.rank shouldBe 50

        repository.listByConfig(stack.tenantId, stack.config.config.id).shouldHaveSize(1)
        repository.findByApiId(stack.tenantId, created.apiId.value) shouldBe created

        repository.deactivate(stack.tenantId, created.id) shouldBe true
        repository.listByConfig(stack.tenantId, stack.config.config.id).shouldBeEmpty()
        repository.findByApiId(stack.tenantId, created.apiId.value).shouldBeNull()
      }
    }
  })
