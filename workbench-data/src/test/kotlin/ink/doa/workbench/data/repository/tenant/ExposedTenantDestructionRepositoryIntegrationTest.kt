package ink.doa.workbench.data.repository.tenant

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.data.persistence.postgres.identity.TenantsTable
import io.kotest.core.spec.style.StringSpec
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
class ExposedTenantDestructionRepositoryIntegrationTest :
  StringSpec({
    "revoke methods return zero when tenant has no related records" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val repository = ExposedTenantDestructionRepository(database)
        val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")

        repository.revokeSessionsByActiveTenant(tenantId, now) shouldBe 0
        repository.revokeBearerTokensByTenant(tenantId, now) shouldBe 0
        repository.revokeAdminUsersByTenant(tenantId, now) shouldBe 0
        repository.expireAccessGrantsByTenant(tenantId, now) shouldBe 0
        repository.cancelPendingInvitationsByTenant(tenantId, now) shouldBe 0
      }
    }

    "softDeleteTenantScopedData completes for tenant without projects" {
      withPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val deletedBy = UUID.randomUUID()
        val repository = ExposedTenantDestructionRepository(database)
        val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")

        repository.softDeleteTenantScopedData(tenantId, now, deletedBy, "cleanup")
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
