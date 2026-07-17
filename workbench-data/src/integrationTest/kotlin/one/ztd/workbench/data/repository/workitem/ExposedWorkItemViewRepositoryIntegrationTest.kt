package one.ztd.workbench.data.repository.workitem

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import one.ztd.workbench.agile.workitem.view.CreateWorkItemViewCommand
import one.ztd.workbench.agile.workitem.view.DeleteWorkItemViewCommand
import one.ztd.workbench.agile.workitem.view.UpdateWorkItemViewCommand
import one.ztd.workbench.agile.workitem.view.WorkItemViewDefaults
import one.ztd.workbench.agile.workitem.view.WorkItemViewVisibility
import one.ztd.workbench.data.support.seedWorkItemStack
import one.ztd.workbench.data.support.withPostgresDatabase

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
              queryAst = WorkItemViewDefaults.EMPTY_QUERY,
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
              queryAst = WorkItemViewDefaults.EMPTY_QUERY,
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
