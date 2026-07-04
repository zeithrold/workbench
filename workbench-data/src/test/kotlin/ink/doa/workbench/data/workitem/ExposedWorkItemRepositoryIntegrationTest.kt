package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.project.model.CreateProjectCommand
import ink.doa.workbench.data.persistence.TenantsTable
import ink.doa.workbench.data.persistence.UsersTable
import ink.doa.workbench.data.project.ExposedProjectRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Tag
import org.testcontainers.containers.PostgreSQLContainer

@Tag("integration")
class ExposedWorkItemRepositoryIntegrationTest :
  StringSpec({
    "findByApiId returns null when issue does not exist" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val actorId = seedUser(database)
        val projects = ExposedProjectRepository(database)
        val project =
          projects.create(
            CreateProjectCommand(
              tenantId = tenantId,
              identifier = "WB",
              name = "Workbench",
              description = null,
              createdBy = actorId,
              leadUserId = actorId,
            )
          )
        val repository = ExposedWorkItemRepository(database)

        repository.findByApiId(tenantId, project.id, "iss_missing").shouldBeNull()
      }
    }

    "countChildrenNotInStatusGroups returns zero for issue without children" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val actorId = seedUser(database)
        val projects = ExposedProjectRepository(database)
        val project =
          projects.create(
            CreateProjectCommand(
              tenantId = tenantId,
              identifier = "WB2",
              name = "Workbench 2",
              description = null,
              createdBy = actorId,
              leadUserId = actorId,
            )
          )
        val repository = ExposedWorkItemRepository(database)

        repository.countChildrenNotInStatusGroups(
          tenantId,
          UUID.randomUUID(),
          setOf("done"),
        ) shouldBe 0
      }
    }
  })

private fun withPostgresDatabase(block: suspend (Database) -> Unit) {
  PostgreSQLContainer("postgres:18-alpine").use { postgres ->
    postgres.start()
    Flyway.configure()
      .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
      .locations("classpath:db/migration")
      .load()
      .migrate()
    val database =
      Database.connect(
        url = postgres.jdbcUrl,
        driver = "org.postgresql.Driver",
        user = postgres.username,
        password = postgres.password,
      )
    kotlinx.coroutines.runBlocking { block(database) }
  }
}

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
