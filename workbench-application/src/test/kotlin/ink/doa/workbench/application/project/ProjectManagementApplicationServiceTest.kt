package ink.doa.workbench.application.project

import ink.doa.workbench.agile.project.ProjectAccessService
import ink.doa.workbench.agile.project.ProjectService
import ink.doa.workbench.agile.project.ProjectSummary
import ink.doa.workbench.agile.project.model.CreateProjectCommand
import ink.doa.workbench.agile.project.model.NonMemberJoinPolicy
import ink.doa.workbench.agile.project.model.NonMemberVisibility
import ink.doa.workbench.agile.project.model.ProjectRecord
import ink.doa.workbench.agile.project.model.ProjectStatus
import ink.doa.workbench.agile.project.model.UpdateProjectCommand
import ink.doa.workbench.application.messaging.support.RecordingDomainEventPublisher
import ink.doa.workbench.application.permission.PermissionBootstrapService
import ink.doa.workbench.identity.UserLookupService
import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.kernel.common.errors.ResourceConflictException
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.kernel.common.warning.WorkbenchWarningCode
import ink.doa.workbench.kernel.common.warning.WorkbenchWarningCollector
import ink.doa.workbench.kernel.common.warning.meta.ProjectDestroyScheduledMeta
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
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
    val warningCollector = mockk<WorkbenchWarningCollector>(relaxed = true)
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val service =
      ProjectManagementApplicationService(
        ProjectManagementDependencies(
          projects = projects,
          userLookupService = userLookupService,
          projectAccess = projectAccess,
          permissionBootstrap = permissionBootstrap,
          infrastructure =
            ProjectManagementInfrastructure(
              domainEventPublisher = publisher,
              warningCollector = warningCollector,
              clock = clock,
            ),
        )
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

    "get throws when project is not visible" {
      val tenantId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val record = sampleProject(tenantId, userId)
      coEvery { projects.get(tenantId, record.apiId.value) } returns record
      coEvery { projectAccess.canViewProject(userId, tenantId, record) } returns false

      shouldThrow<ResourceNotFoundException> {
          runBlocking { service.get(tenantId, userId, record.apiId.value) }
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND
    }

    "update maps project record to view" {
      val tenantId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val record = sampleProject(tenantId, actorId)
      coEvery { projects.update(any()) } returns record.copy(name = "Renamed")
      coEvery { userLookupService.requireUser(actorId) } returns sampleUser(actorId)

      val view = runBlocking {
        service.update(
          UpdateProjectCommand(
            tenantId = tenantId,
            projectId = record.id,
            identifier = null,
            name = "Renamed",
            description = record.description,
            nonMemberVisibility = null,
            nonMemberJoinPolicy = null,
            updatedBy = actorId,
          )
        )
      }

      view.name shouldBe "Renamed"
    }

    "archive returns archived project view" {
      val tenantId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val record = sampleProject(tenantId, actorId)
      val archived = record.copy(status = ProjectStatus.ARCHIVED)
      coEvery { projects.archive(tenantId, record.id, any(), actorId) } returns archived
      coEvery { userLookupService.requireUser(actorId) } returns sampleUser(actorId)

      val view = runBlocking { service.archive(tenantId, record.id, actorId) }

      view.status shouldBe "archived"
    }

    "requestDestroy throws when project already destroying" {
      val tenantId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val record = sampleProject(tenantId, actorId).copy(status = ProjectStatus.DESTROYING)
      coEvery { projects.get(tenantId, record.apiId.value) } returns record

      shouldThrow<ResourceConflictException> {
          runBlocking {
            service.requestDestroy(
              tenantId = tenantId,
              tenantPublicId = PublicId.new("ten"),
              projectPublicId = record.apiId.value,
              actorUserId = actorId,
              deleteReason = "cleanup",
            )
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.PROJECT_ALREADY_DESTROYING
    }

    "requestDestroy publishes event and returns destroying view" {
      val tenantId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val record = sampleProject(tenantId, actorId)
      val destroying = record.copy(status = ProjectStatus.DESTROYING)
      val actor = sampleUser(actorId)
      coEvery { projects.get(tenantId, record.apiId.value) } returns record
      coEvery { userLookupService.requireAuthenticatedUser(actorId) } returns actor
      coEvery { projects.requestDestroy(any()) } returns destroying
      coEvery { userLookupService.requireUser(actorId) } returns actor

      val view = runBlocking {
        service.requestDestroy(
          tenantId = tenantId,
          tenantPublicId = PublicId.new("ten"),
          projectPublicId = record.apiId.value,
          actorUserId = actorId,
          deleteReason = "cleanup",
        )
      }

      view.status shouldBe "destroying"
      verify(exactly = 1) {
        warningCollector.warn(
          WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED,
          ProjectDestroyScheduledMeta(
            project = ProjectSummary.from(destroying),
            deleteReason = "cleanup",
          ),
        )
      }
    }

    "get returns view when project is visible" {
      val tenantId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val record = sampleProject(tenantId, userId)
      coEvery { projects.get(tenantId, record.apiId.value) } returns record
      coEvery { projectAccess.canViewProject(userId, tenantId, record) } returns true
      coEvery { userLookupService.requireUser(userId) } returns sampleUser(userId)

      val view = runBlocking { service.get(tenantId, userId, record.apiId.value) }

      view.identifier shouldBe "WB"
    }

    "unarchive returns active project view" {
      val tenantId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val record = sampleProject(tenantId, actorId)
      val active = record.copy(status = ProjectStatus.ACTIVE)
      coEvery { projects.unarchive(tenantId, record.id) } returns active
      coEvery { userLookupService.requireUser(actorId) } returns sampleUser(actorId)

      val view = runBlocking { service.unarchive(tenantId, record.id) }

      view.status shouldBe "active"
    }

    "create maps project without lead user" {
      val tenantId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val record = sampleProject(tenantId, actorId).copy(leadUserId = null)
      coEvery { projects.create(any()) } returns record
      coEvery { permissionBootstrap.provisionProjectCreator(any(), any(), any(), any()) } returns
        Unit

      val view = runBlocking {
        service.create(
          CreateProjectCommand(
            tenantId = tenantId,
            identifier = "WB",
            name = "Workbench",
            description = null,
            createdBy = actorId,
            leadUserId = actorId,
          ),
          actorUserId = actorId,
        )
      }

      view.lead.shouldBeNull()
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
