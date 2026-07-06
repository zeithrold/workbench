package ink.doa.workbench.data.repository.project

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.project.model.CreateProjectCommand
import ink.doa.workbench.core.project.model.NonMemberJoinPolicy
import ink.doa.workbench.core.project.model.NonMemberVisibility
import ink.doa.workbench.core.project.model.ProjectStatus
import ink.doa.workbench.core.project.model.UpdateProjectCommand
import ink.doa.workbench.data.persistence.postgres.identity.TenantsTable
import ink.doa.workbench.data.persistence.postgres.identity.UsersTable
import ink.doa.workbench.data.support.withCorePostgresDatabase
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Tags("integration")
class ExposedProjectRepositoryIntegrationTest :
  StringSpec({
    "create find update and list projects" {
      withCorePostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val actorId = seedUser(database)
        val repository = ExposedProjectRepository(database)

        val created =
          repository.create(
            CreateProjectCommand(
              tenantId = tenantId,
              identifier = "WB",
              name = "Workbench",
              description = "Main",
              createdBy = actorId,
              leadUserId = actorId,
            )
          )

        created.identifier shouldBe "WB"
        repository.findByApiId(tenantId, created.apiId.value).shouldNotBeNull()
        repository.list(tenantId, identifier = "WB") shouldHaveSize 1

        val updated =
          repository.update(
            UpdateProjectCommand(
              tenantId = tenantId,
              projectId = created.id,
              name = "Workbench Updated",
              identifier = null,
              description = "Updated",
              nonMemberVisibility = null,
              nonMemberJoinPolicy = null,
              updatedBy = actorId,
            )
          )

        updated.name shouldBe "Workbench Updated"
      }
    }

    "archive reactivate rename identifier and find by id" {
      withCorePostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val actorId = seedUser(database)
        val repository = ExposedProjectRepository(database)
        val archivedAt = OffsetDateTime.now(ZoneOffset.UTC)

        val created =
          repository.create(
            CreateProjectCommand(
              tenantId = tenantId,
              identifier = "OLD",
              name = "Rename Me",
              description = null,
              createdBy = actorId,
              leadUserId = actorId,
            )
          )

        repository.findById(tenantId, created.id)?.identifier shouldBe "OLD"

        val archived = repository.markArchived(tenantId, created.id, archivedAt, actorId)
        archived.status shouldBe ProjectStatus.ARCHIVED

        val active = repository.markActive(tenantId, created.id)
        active.status shouldBe ProjectStatus.ACTIVE

        val renamed =
          repository.update(
            UpdateProjectCommand(
              tenantId = tenantId,
              projectId = created.id,
              name = null,
              identifier = "NEW",
              description = null,
              nonMemberVisibility = NonMemberVisibility.READ_WRITE,
              nonMemberJoinPolicy = NonMemberJoinPolicy.OPEN,
              updatedBy = actorId,
            )
          )

        renamed.identifier shouldBe "NEW"
        renamed.nonMemberVisibility shouldBe NonMemberVisibility.READ_WRITE
        renamed.nonMemberJoinPolicy shouldBe NonMemberJoinPolicy.OPEN
      }
    }

    "markDestroying finalizeDestroy and updateStatus manage lifecycle" {
      withCorePostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val actorId = seedUser(database)
        val repository = ExposedProjectRepository(database)
        val created =
          repository.create(
            CreateProjectCommand(
              tenantId = tenantId,
              identifier = "DEL",
              name = "Delete Me",
              description = null,
              createdBy = actorId,
              leadUserId = actorId,
            )
          )
        val destroying =
          repository.markDestroying(
            tenantId = tenantId,
            projectId = created.id,
            deletedBy = actorId,
            deleteReason = "cleanup",
          )
        destroying.status shouldBe ProjectStatus.DESTROYING

        repository.updateStatus(tenantId, created.id, ProjectStatus.ACTIVE) shouldBe true
        repository.findById(tenantId, created.id)?.status shouldBe ProjectStatus.ACTIVE

        repository.markDestroying(tenantId, created.id, actorId, "final").status shouldBe
          ProjectStatus.DESTROYING
        val deletedAt = OffsetDateTime.now(ZoneOffset.UTC)
        repository.finalizeDestroy(
          tenantId = tenantId,
          projectId = created.id,
          deletedAt = deletedAt,
          deletedBy = actorId,
          deleteReason = "final",
        ) shouldBe true
        repository.findById(tenantId, created.id).shouldBeNull()
      }
    }
  })

private fun seedTenant(database: Database): UUID {
  val tenantId = UUID.randomUUID()
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  transaction(database) {
    TenantsTable.insert {
      it[id] = tenantId.toKotlinUuid()
      it[apiId] = PublicId.new("ten").value
      it[name] = "Test Tenant"
      it[slug] = "test-${tenantId.toString().take(8)}"
      it[timezone] = "UTC"
      it[locale] = "en-US"
      it[createdAt] = now
      it[updatedAt] = now
    }
  }
  return tenantId
}

private fun seedUser(database: Database): UUID {
  val userId = UUID.randomUUID()
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  transaction(database) {
    UsersTable.insert {
      it[id] = userId.toKotlinUuid()
      it[apiId] = PublicId.new("usr").value
      it[displayName] = "Ada"
      it[primaryEmail] = "ada-${userId.toString().take(8)}@example.test"
      it[createdAt] = now
      it[updatedAt] = now
    }
  }
  return userId
}
