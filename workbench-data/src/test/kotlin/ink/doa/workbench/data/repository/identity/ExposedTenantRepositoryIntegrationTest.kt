package ink.doa.workbench.data.repository.identity

import ink.doa.workbench.core.common.errors.ResourceConflictException
import ink.doa.workbench.core.identity.model.CreateTenantCommand
import ink.doa.workbench.core.identity.model.FinalizeTenantDestroyCommand
import ink.doa.workbench.core.identity.model.TenantStatus
import ink.doa.workbench.core.identity.model.UpdateTenantCommand
import ink.doa.workbench.data.support.seedUser
import ink.doa.workbench.data.support.withPostgresDatabase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

@Tags("integration")
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
  })
