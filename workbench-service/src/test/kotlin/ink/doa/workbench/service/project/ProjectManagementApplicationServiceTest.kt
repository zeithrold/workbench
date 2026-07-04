package ink.doa.workbench.service.project

import ink.doa.workbench.agile.project.ProjectAccessService
import ink.doa.workbench.agile.project.ProjectService
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.core.project.model.CreateProjectCommand
import ink.doa.workbench.core.project.model.NonMemberJoinPolicy
import ink.doa.workbench.core.project.model.NonMemberVisibility
import ink.doa.workbench.core.project.model.ProjectRecord
import ink.doa.workbench.core.project.model.ProjectStatus
import ink.doa.workbench.security.identity.UserLookupService
import ink.doa.workbench.security.permission.PermissionBootstrapService
import ink.doa.workbench.service.messaging.support.RecordingDomainEventPublisher
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

class ProjectManagementApplicationServiceTest :
  StringSpec({
    val projects = mockk<ProjectService>()
    val userLookupService = mockk<UserLookupService>()
    val projectAccess = mockk<ProjectAccessService>()
    val permissionBootstrap = mockk<PermissionBootstrapService>()
    val publisher = RecordingDomainEventPublisher()
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val service =
      ProjectManagementApplicationService(
        projects,
        userLookupService,
        projectAccess,
        permissionBootstrap,
        publisher,
        clock,
      )

    "create provisions project creator permissions" {
      val tenantId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val record = sampleProject(tenantId, actorId)
      coEvery { projects.create(any()) } returns record
      coEvery { permissionBootstrap.provisionProjectCreator(any(), any(), any(), any()) } returns
        Unit
      coEvery { userLookupService.requireUser(actorId) } returns sampleUser(actorId)

      val view = runBlocking {
        service.create(
          CreateProjectCommand(
            tenantId = tenantId,
            identifier = "WB",
            name = "Workbench",
            description = "Main project",
            createdBy = actorId,
            leadUserId = actorId,
          ),
          actorUserId = actorId,
        )
      }

      view.identifier shouldBe "WB"
      coVerify(exactly = 1) {
        permissionBootstrap.provisionProjectCreator(
          tenantId = tenantId,
          projectId = record.id,
          userId = actorId,
          actorUserId = actorId,
        )
      }
    }

    "list maps visible projects to views" {
      val tenantId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val record = sampleProject(tenantId, userId)
      coEvery { projectAccess.listVisibleProjects(userId, tenantId, null) } returns listOf(record)
      coEvery { userLookupService.requireUser(userId) } returns sampleUser(userId)

      val views = runBlocking { service.list(tenantId, userId, identifier = null) }

      views.single().name shouldBe "Workbench"
    }
  })

private fun sampleProject(tenantId: UUID, actorId: UUID): ProjectRecord =
  ProjectRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("prj"),
    tenantId = tenantId,
    identifier = "WB",
    name = "Workbench",
    description = "Main project",
    status = ProjectStatus.ACTIVE,
    nonMemberVisibility = NonMemberVisibility.INVISIBLE,
    nonMemberJoinPolicy = NonMemberJoinPolicy.ADMIN_ONLY,
    leadUserId = actorId,
    createdBy = actorId,
  )

private fun sampleUser(id: UUID): UserRecord =
  UserRecord(
    id = id,
    apiId = PublicId.new("usr"),
    displayName = "Ada",
    primaryEmail = "ada@example.test",
  )
