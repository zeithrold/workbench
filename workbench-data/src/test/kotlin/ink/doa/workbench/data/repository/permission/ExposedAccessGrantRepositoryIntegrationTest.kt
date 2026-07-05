package ink.doa.workbench.data.permission

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.model.CreateUserCommand
import ink.doa.workbench.core.permission.CreateAccessGrantCommand
import ink.doa.workbench.core.permission.GrantScope
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.data.identity.ExposedUserRepository
import ink.doa.workbench.data.persistence.TenantsTable
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
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
class ExposedAccessGrantRepositoryIntegrationTest :
  StringSpec({
    "create persists grant and listByTenant returns it" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val users = ExposedUserRepository(database)
        val repository = ExposedAccessGrantRepository(database)
        val user = users.create(CreateUserCommand("Ada", "ada-grants@example.test"))
        val validFrom = OffsetDateTime.parse("2026-07-04T00:00:00Z")

        val created =
          repository.create(
            CreateAccessGrantCommand(
              scope = GrantScope.TENANT,
              tenantId = tenantId,
              subjectUserId = user.id,
              action = AuthorizationAction("project.read"),
              resourcePattern = "project:*",
              validFrom = validFrom,
              grantedBy = user.id,
            )
          )

        repository.listByTenant(tenantId).shouldHaveSize(1)
        repository.findByApiId(created.apiId.value)?.subjectUserId shouldBe user.id
      }
    }

    "listBySubject and listActiveForSubject filter grants" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val users = ExposedUserRepository(database)
        val repository = ExposedAccessGrantRepository(database)
        val user = users.create(CreateUserCommand("Bob", "bob-active@example.test"))
        val validFrom = OffsetDateTime.parse("2026-07-04T00:00:00Z")
        val expiredAt = OffsetDateTime.parse("2026-07-04T01:00:00Z")

        val active =
          repository.create(
            CreateAccessGrantCommand(
              scope = GrantScope.TENANT,
              tenantId = tenantId,
              subjectUserId = user.id,
              action = AuthorizationAction("project.read"),
              resourcePattern = "project:*",
              validFrom = validFrom,
              grantedBy = user.id,
            )
          )
        val expired =
          repository.create(
            CreateAccessGrantCommand(
              scope = GrantScope.TENANT,
              tenantId = tenantId,
              subjectUserId = user.id,
              action = AuthorizationAction("tenant.read"),
              resourcePattern = "tenant:*",
              validFrom = validFrom,
              validTo = expiredAt,
              grantedBy = user.id,
            )
          )

        repository
          .listBySubject(user.id, GrantScope.TENANT, tenantId, null)
          .map { it.id }
          .toSet() shouldBe setOf(active.id, expired.id)
        repository
          .listActiveForSubject(
            subjectUserId = user.id,
            scope = GrantScope.TENANT,
            tenantId = tenantId,
            projectId = null,
            at = OffsetDateTime.parse("2026-07-04T12:00:00Z"),
          )
          .single()
          .id shouldBe active.id
        repository.expire(expired.id, expiredAt) shouldBe true
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
