package ink.doa.workbench.jobs.project

import ink.doa.workbench.agile.project.ProjectDestructionService
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.model.TenantRecord
import ink.doa.workbench.core.identity.model.TenantStatus
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.core.port.locking.DistributedLockService
import ink.doa.workbench.core.project.ProjectRepository
import ink.doa.workbench.core.project.events.ProjectDestroyRequestedEvent
import ink.doa.workbench.core.project.events.ProjectDomainEvents
import ink.doa.workbench.core.project.model.ProjectRecord
import ink.doa.workbench.core.project.model.ProjectStatus
import ink.doa.workbench.service.messaging.support.RecordingDomainEventPublisher
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

class ProjectDestroyRequestedEventHandlerTest :
  StringSpec({
    val tenants = mockk<TenantRepository>()
    val projects = mockk<ProjectRepository>()
    val users = mockk<UserRepository>()
    val projectDestructionService = mockk<ProjectDestructionService>()
    val publisher = RecordingDomainEventPublisher()
    val distributedLockService =
      object : DistributedLockService {
        override fun <T> withLock(
          name: String,
          wait: Duration,
          lease: Duration,
          block: () -> T,
        ): T = block()
      }
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val lookup = ProjectDestroyLookupSupport(tenants, projects, users)
    val runtime =
      ProjectDestroyRuntimeSupport(
        projectDestructionService,
        publisher,
        distributedLockService,
        clock,
      )
    val handler = ProjectDestroyRequestedEventHandler(lookup, runtime)

    beforeTest {
      publisher.clear()
    }

    "handle executes destruction and publishes destroyed event" {
      val tenant = sampleTenant()
      val project = sampleProject(tenant.id)
      val actor = sampleUser()
      val payload =
        ProjectDestroyRequestedEvent(
          tenantId = tenant.apiId.value,
          projectId = project.apiId.value,
          requestedBy = actor.apiId.value,
          deleteReason = "cleanup",
          requestedAt = "2026-07-04T00:00:00Z",
        )

      coEvery { tenants.findByApiId(tenant.apiId.value) } returns tenant
      coEvery { projects.findByApiId(tenant.id, project.apiId.value) } returns project
      coEvery { users.findByApiId(actor.apiId.value) } returns actor
      coEvery {
        projectDestructionService.execute(
          tenantId = tenant.id,
          projectId = project.id,
          deletedBy = actor.id,
          deleteReason = "cleanup",
        )
      } returns true

      runBlocking { handler.handle(payload) }

      publisher.published.single().spec shouldBe ProjectDomainEvents.Destroyed
    }

    "handle skips when tenant is missing" {
      coEvery { tenants.findByApiId("ten_missing") } returns null

      runBlocking {
        handler.handle(
          ProjectDestroyRequestedEvent(
            tenantId = "ten_missing",
            projectId = "prj_missing",
            requestedBy = "usr_missing",
            deleteReason = null,
            requestedAt = "2026-07-04T00:00:00Z",
          )
        )
      }

      publisher.published shouldBe emptyList()
    }

    "handle skips when project is missing" {
      val tenant = sampleTenant()
      coEvery { tenants.findByApiId(tenant.apiId.value) } returns tenant
      coEvery { projects.findByApiId(tenant.id, "prj_missing") } returns null

      runBlocking {
        handler.handle(
          ProjectDestroyRequestedEvent(
            tenantId = tenant.apiId.value,
            projectId = "prj_missing",
            requestedBy = "usr_missing",
            deleteReason = null,
            requestedAt = "2026-07-04T00:00:00Z",
          )
        )
      }

      publisher.published shouldBe emptyList()
    }

    "handle skips when actor is missing" {
      val tenant = sampleTenant()
      val project = sampleProject(tenant.id)
      coEvery { tenants.findByApiId(tenant.apiId.value) } returns tenant
      coEvery { projects.findByApiId(tenant.id, project.apiId.value) } returns project
      coEvery { users.findByApiId("usr_missing") } returns null

      runBlocking {
        handler.handle(
          ProjectDestroyRequestedEvent(
            tenantId = tenant.apiId.value,
            projectId = project.apiId.value,
            requestedBy = "usr_missing",
            deleteReason = null,
            requestedAt = "2026-07-04T00:00:00Z",
          )
        )
      }

      publisher.published shouldBe emptyList()
    }

    "handle skips publishing when destruction does not complete" {
      val tenant = sampleTenant()
      val project = sampleProject(tenant.id)
      val actor = sampleUser()
      val payload =
        ProjectDestroyRequestedEvent(
          tenantId = tenant.apiId.value,
          projectId = project.apiId.value,
          requestedBy = actor.apiId.value,
          deleteReason = "cleanup",
          requestedAt = "2026-07-04T00:00:00Z",
        )

      coEvery { tenants.findByApiId(tenant.apiId.value) } returns tenant
      coEvery { projects.findByApiId(tenant.id, project.apiId.value) } returns project
      coEvery { users.findByApiId(actor.apiId.value) } returns actor
      coEvery {
        projectDestructionService.execute(
          tenantId = tenant.id,
          projectId = project.id,
          deletedBy = actor.id,
          deleteReason = "cleanup",
        )
      } returns false

      runBlocking { handler.handle(payload) }

      publisher.published shouldBe emptyList()
    }
  })

private fun sampleTenant(): TenantRecord =
  TenantRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("ten"),
    slug = "acme",
    name = "Acme",
    status = TenantStatus.ACTIVE,
    createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
  )

private fun sampleProject(tenantId: UUID): ProjectRecord =
  ProjectRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("prj"),
    tenantId = tenantId,
    identifier = "WB",
    name = "Workbench",
    description = null,
    status = ProjectStatus.DESTROYING,
  )

private fun sampleUser(): UserRecord =
  UserRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("usr"),
    displayName = "Ada",
    primaryEmail = "ada@example.test",
  )
