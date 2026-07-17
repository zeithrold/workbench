package one.ztd.workbench.agile.workitem

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.agile.project.ProjectRepository
import one.ztd.workbench.agile.project.model.ProjectRecord
import one.ztd.workbench.agile.project.model.ProjectStatus
import one.ztd.workbench.agile.workitem.view.CreateWorkItemViewCommand
import one.ztd.workbench.agile.workitem.view.DeleteWorkItemViewCommand
import one.ztd.workbench.agile.workitem.view.UpdateWorkItemViewCommand
import one.ztd.workbench.agile.workitem.view.WorkItemViewDefaults
import one.ztd.workbench.agile.workitem.view.WorkItemViewRecord
import one.ztd.workbench.agile.workitem.view.WorkItemViewRepository
import one.ztd.workbench.agile.workitem.view.WorkItemViewVisibility
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.kernel.common.errors.PermissionDeniedException
import one.ztd.workbench.kernel.common.ids.PublicId

class WorkItemViewServiceTest :
  FunSpec({
    val views = mockk<WorkItemViewRepository>()
    val access = mockk<WorkItemViewAccessService>()
    val users = mockk<UserRepository>()
    val projects = mockk<ProjectRepository>()
    val service = WorkItemViewService(views, access, users, projects)

    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val actorId = UUID.randomUUID()
    val now = OffsetDateTime.parse("2026-01-01T00:00:00Z")

    val sampleRecord =
      WorkItemViewRecord(
        id = UUID.randomUUID(),
        apiId = PublicId("wiv_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        tenantId = tenantId,
        projectId = projectId,
        ownerId = actorId,
        ownerApiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
        name = "Backlog",
        description = null,
        visibility = WorkItemViewVisibility.PRIVATE,
        queryAst = WorkItemViewDefaults.EMPTY_QUERY,
        displayFields = WorkItemViewDefaults.EMPTY_DISPLAY_FIELDS,
        createdAt = now,
        updatedAt = now,
      )

    beforeTest {
      coEvery { users.findById(actorId) } returns
        UserRecord(
          id = actorId,
          apiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
          displayName = "Ada",
          primaryEmail = "ada@example.test",
        )
      coEvery { projects.findById(tenantId, projectId) } returns
        ProjectRecord(
          id = projectId,
          apiId = PublicId("prj_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          tenantId = tenantId,
          identifier = "CORE",
          name = "Core",
          description = null,
          status = ProjectStatus.ACTIVE,
          leadUserId = actorId,
          createdBy = actorId,
        )
    }

    test("list filters views by read access") {
      val hidden =
        sampleRecord.copy(
          apiId = PublicId("wiv_01JABCDEFGHJKMNPQRSTVWXYZ2"),
          ownerId = UUID.randomUUID(),
        )
      coEvery { views.listByProject(tenantId, projectId) } returns listOf(sampleRecord, hidden)
      coEvery { access.canRead(sampleRecord, actorId) } returns true
      coEvery { access.canRead(hidden, actorId) } returns false

      service.list(tenantId, projectId, actorId).single().id shouldBe sampleRecord.apiId.value
    }

    test("create validates access and persists view") {
      val command =
        CreateWorkItemViewCommand(
          tenantId = tenantId,
          projectId = projectId,
          ownerId = actorId,
          name = "Backlog",
          description = null,
          visibility = WorkItemViewVisibility.PRIVATE,
          queryAst = WorkItemViewDefaults.EMPTY_QUERY,
          displayFields = WorkItemViewDefaults.EMPTY_DISPLAY_FIELDS,
        )
      coEvery { access.requireCreate(tenantId, projectId, actorId) } returns Unit
      coEvery { views.create(command) } returns sampleRecord

      service.create(command).name shouldBe "Backlog"
    }

    test("get returns assembled view") {
      coEvery { views.findByApiId(tenantId, sampleRecord.apiId.value, projectId) } returns
        sampleRecord
      coEvery { access.requireRead(sampleRecord, actorId) } returns Unit

      service.get(tenantId, projectId, sampleRecord.apiId.value, actorId).name shouldBe "Backlog"
    }

    test("update persists changes when allowed") {
      coEvery { views.findByApiId(tenantId, sampleRecord.apiId.value, projectId) } returns
        sampleRecord
      coEvery { access.requireManage(sampleRecord, actorId) } returns Unit
      coEvery { views.update(any()) } returns sampleRecord.copy(name = "Renamed")

      service
        .update(
          UpdateWorkItemViewCommand(
            tenantId = tenantId,
            viewApiId = sampleRecord.apiId.value,
            projectId = projectId,
            actorUserId = actorId,
            name = "Renamed",
          )
        )
        .name shouldBe "Renamed"
    }

    test("update requires manage permission") {
      coEvery { views.findByApiId(tenantId, sampleRecord.apiId.value, projectId) } returns
        sampleRecord
      coEvery { access.requireManage(sampleRecord, actorId) } throws
        PermissionDeniedException(
          one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode.WORK_ITEM_VIEW_MANAGE_DENIED
        )

      shouldThrow<PermissionDeniedException> {
        service.update(
          UpdateWorkItemViewCommand(
            tenantId = tenantId,
            viewApiId = sampleRecord.apiId.value,
            projectId = projectId,
            actorUserId = actorId,
            name = "Renamed",
          )
        )
      }
    }

    test("delete removes view after manage check") {
      coEvery { views.findByApiId(tenantId, sampleRecord.apiId.value, projectId) } returns
        sampleRecord
      coEvery { access.requireManage(sampleRecord, actorId) } returns Unit
      coEvery {
        views.delete(
          DeleteWorkItemViewCommand(
            tenantId = tenantId,
            viewApiId = sampleRecord.apiId.value,
            projectId = projectId,
            actorUserId = actorId,
          )
        )
      } returns true

      service.delete(
        DeleteWorkItemViewCommand(
          tenantId = tenantId,
          viewApiId = sampleRecord.apiId.value,
          projectId = projectId,
          actorUserId = actorId,
        )
      )
    }
  })
