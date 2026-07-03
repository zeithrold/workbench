package doa.ink.workbench.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.testcontainers.containers.PostgreSQLContainer

@Tag("integration")
class PostgresMigrationIntegrationTest :
  StringSpec({
    "Flyway migrations run on PostgreSQL" {
      PostgreSQLContainer("postgres:18-alpine").use { postgres ->
        postgres.start()

        val result =
          Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        result.migrationsExecuted shouldBe 14
      }
    }
  })
