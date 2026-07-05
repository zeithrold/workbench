package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.workitem.view.CreateWorkItemViewCommand
import ink.doa.workbench.core.workitem.view.DeleteWorkItemViewCommand
import ink.doa.workbench.core.workitem.view.UpdateWorkItemViewCommand
import ink.doa.workbench.core.workitem.view.WorkItemViewDefaults
import ink.doa.workbench.core.workitem.view.WorkItemViewVisibility
import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.data.support.withPostgresDatabase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag

@Tag("integration")
class ExposedWorkItemViewRepositoryIntegrationTest :
  StringSpec({
    "creates updates lists and deletes project and tenant views" {
      withPostgresDatabase { database ->
        val stack = seedWorkItemStack(database)
        val views = ExposedWorkItemViewRepository(database)

        val projectView =
          views.create(
            CreateWorkItemViewCommand(
              tenantId = stack.tenantId,
              projectId = stack.projectId,
              ownerId = stack.actorId,
              name = "Project backlog",
              description = "Team backlog",
              visibility = WorkItemViewVisibility.PROJECT,
              filterAst = WorkItemViewDefaults.EMPTY_FILTER,
              sortAst = WorkItemViewDefaults.EMPTY_SORT,
              groupAst = WorkItemViewDefaults.EMPTY_GROUP,
              displayFields = WorkItemViewDefaults.EMPTY_DISPLAY_FIELDS,
            )
          )

        projectView.name shouldBe "Project backlog"
        views.listByProject(stack.tenantId, stack.projectId).shouldHaveSize(1)

        val tenantView =
          views.create(
            CreateWorkItemViewCommand(
              tenantId = stack.tenantId,
              projectId = null,
              ownerId = stack.actorId,
              name = "Tenant inbox",
              description = null,
              visibility = WorkItemViewVisibility.TENANT,
              filterAst = WorkItemViewDefaults.EMPTY_FILTER,
              sortAst = WorkItemViewDefaults.EMPTY_SORT,
              groupAst = WorkItemViewDefaults.EMPTY_GROUP,
              displayFields = WorkItemViewDefaults.EMPTY_DISPLAY_FIELDS,
            )
          )

        views.listTenantScoped(stack.tenantId).single().id shouldBe tenantView.id

        val updated =
          views.update(
            UpdateWorkItemViewCommand(
              tenantId = stack.tenantId,
              viewApiId = projectView.apiId.value,
              projectId = stack.projectId,
              actorUserId = stack.actorId,
              name = "Renamed backlog",
              visibility = WorkItemViewVisibility.PRIVATE,
            )
          )

        updated.name shouldBe "Renamed backlog"
        updated.visibility shouldBe WorkItemViewVisibility.PRIVATE

        views.delete(
          DeleteWorkItemViewCommand(
            tenantId = stack.tenantId,
            viewApiId = projectView.apiId.value,
            projectId = stack.projectId,
            actorUserId = stack.actorId,
          )
        ) shouldBe true

        views.findByApiId(stack.tenantId, projectView.apiId.value, stack.projectId).shouldBeNull()
        views.listByProject(stack.tenantId, stack.projectId).shouldHaveSize(0)
      }
    }
  })
