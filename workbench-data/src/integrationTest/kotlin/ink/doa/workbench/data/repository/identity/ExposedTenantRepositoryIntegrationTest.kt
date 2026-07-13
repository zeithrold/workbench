package ink.doa.workbench.data.repository.identity

import ink.doa.workbench.data.messaging.ExposedDomainEventOutbox
import ink.doa.workbench.data.persistence.postgres.workitem.DomainOutboxTable
import ink.doa.workbench.data.support.seedUser
import ink.doa.workbench.data.support.withPostgresDatabase
import ink.doa.workbench.kernel.common.errors.ResourceConflictException
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.kernel.messaging.DomainEventEncoder
import ink.doa.workbench.tenant.model.CreateTenantCommand
import ink.doa.workbench.tenant.model.FinalizeTenantDestroyCommand
import ink.doa.workbench.tenant.model.TenantStatus
import ink.doa.workbench.tenant.model.UpdateTenantCommand
import ink.doa.workbench.tenant.tenant.events.TenantDestroyRequestedEvent
import ink.doa.workbench.tenant.tenant.events.TenantDomainEvents
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ExposedTenantRepositoryIntegrationTest :
  StringSpec({
    "create update and find tenant by slug" {
      withPostgresDatabase { database ->
        val repository = ExposedTenantRepository(database)
        val created =
          repository.create(
            CreateTenantCommand(
              name = "Acme",
              slug = "acme-repo-test",
              timezone = "UTC",
              locale = "en-US",
              status = TenantStatus.ACTIVE,
            )
          )

        created.slug shouldBe "acme-repo-test"
        repository.findBySlug("acme-repo-test").shouldNotBeNull()
        repository.existsBySlug("acme-repo-test") shouldBe true

        val updated =
          repository.update(
            UpdateTenantCommand(
              tenantId = created.id,
              name = "Acme Updated",
              slug = null,
              timezone = "Asia/Shanghai",
              locale = null,
              status = null,
            )
          )

        updated.name shouldBe "Acme Updated"
        updated.timezone shouldBe "Asia/Shanghai"
      }
    }

    "create rejects duplicate slug" {
      withPostgresDatabase { database ->
        val repository = ExposedTenantRepository(database)
        repository.create(
          CreateTenantCommand(
            name = "First",
            slug = "duplicate-slug",
            timezone = "UTC",
            locale = "en-US",
            status = TenantStatus.ACTIVE,
          )
        )

        shouldThrow<ResourceConflictException> {
          repository.create(
            CreateTenantCommand(
              name = "Second",
              slug = "duplicate-slug",
              timezone = "UTC",
              locale = "en-US",
              status = TenantStatus.ACTIVE,
            )
          )
        }
      }
    }

    "find by api id list and admin queries" {
      withPostgresDatabase { database ->
        val repository = ExposedTenantRepository(database)
        val created =
          repository.create(
            CreateTenantCommand(
              name = "Lookup",
              slug = "lookup-tenant",
              timezone = "UTC",
              locale = "en-US",
              status = TenantStatus.ACTIVE,
            )
          )

        repository.findByApiId(created.apiId.value)?.id shouldBe created.id
        repository.findByApiIdForAdmin(created.apiId.value)?.id shouldBe created.id
        repository.findById(created.id)?.slug shouldBe "lookup-tenant"
        repository.findByIds(listOf(created.id)).single().id shouldBe created.id
        repository.findByIds(emptyList()) shouldBe emptyList()
        repository.list("lookup-tenant").single().id shouldBe created.id
        repository.listForAdmin(null).map { it.id } shouldContain created.id
      }
    }

    "markDestroying and finalizeDestroy lifecycle" {
      withPostgresDatabase { database ->
        val repository = ExposedTenantRepository(database)
        val actorId = seedUser(database)
        val created =
          repository.create(
            CreateTenantCommand(
              name = "Lifecycle",
              slug = "lifecycle-tenant",
              timezone = "UTC",
              locale = "en-US",
              status = TenantStatus.ACTIVE,
            )
          )

        val destroying = repository.markDestroying(created.id)
        destroying.status shouldBe TenantStatus.DESTROYING
        repository.findById(created.id).shouldBeNull()
        repository.findByIdForDestruction(created.id).shouldNotBeNull()

        shouldThrow<ResourceConflictException> { repository.markDestroying(created.id) }

        repository.finalizeDestroy(
          FinalizeTenantDestroyCommand(
            tenantId = created.id,
            deletedBy = actorId,
            deleteReason = "test cleanup",
          )
        ) shouldBe true
        repository.findByIdForDestruction(created.id).shouldBeNull()
      }
    }

    "requestDestroy marks destroying and enqueues tenant.destroy_requested" {
      withPostgresDatabase { database ->
        val outbox = ExposedDomainEventOutbox(database, DomainEventEncoder(Clock.systemUTC()))
        val repository = ExposedTenantRepository(database, outbox)
        val actorId = seedUser(database)
        val created =
          repository.create(
            CreateTenantCommand(
              name = "Destroy",
              slug = "destroy-tenant",
              timezone = "UTC",
              locale = "en-US",
              status = TenantStatus.ACTIVE,
            )
          )
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val payload =
          TenantDestroyRequestedEvent.from(
            tenant = created,
            deleteReason = "cleanup",
            requestedAt = now,
            requestedByPublicId = PublicId.new("usr"),
          )

        val destroying =
          repository.requestDestroy(
            tenantId = created.id,
            tenantApiId = created.apiId.value,
            payload = payload,
          )

        destroying.status shouldBe TenantStatus.DESTROYING
        transaction(database) {
          DomainOutboxTable.selectAll()
            .where { DomainOutboxTable.eventType eq TenantDomainEvents.DestroyRequested.type }
            .count() shouldBe 1
        }
      }
    }
  })
