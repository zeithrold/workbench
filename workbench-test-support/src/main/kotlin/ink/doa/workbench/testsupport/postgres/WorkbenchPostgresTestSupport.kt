package ink.doa.workbench.testsupport.postgres

import kotlinx.coroutines.runBlocking
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.context.DynamicPropertyRegistry

object WorkbenchPostgresTestSupport {
  private val moduleLeases = mutableMapOf<String, PostgresTestDatabaseLease>()

  fun withDatabase(
    spec: MigrationSpec = MigrationSpec.Full,
    block: suspend (Database) -> Unit,
  ) {
    openDatabase(spec).use { lease ->
      runBlocking { block(lease.database) }
    }
  }

  fun openDatabase(spec: MigrationSpec = MigrationSpec.Full): PostgresTestDatabaseLease =
    PostgresDatabaseProvisioner.openDatabase(spec, dropOnClose = true)

  fun moduleDatabase(spec: MigrationSpec = MigrationSpec.Full): PostgresTestDatabaseLease {
    val key =
      when (spec) {
        is MigrationSpec.Custom -> spec.cacheKey
        else -> spec.templateKey()
      }
    synchronized(moduleLeases) {
      return moduleLeases.getOrPut(key) {
        PostgresDatabaseProvisioner.openDatabase(spec, dropOnClose = false)
      }
    }
  }

  fun customMigration(
    locations: Array<String> = MigrationSpec.Full.locations(),
    migrateOnOpen: Boolean = true,
    configure: FluentConfiguration.() -> FluentConfiguration = { this },
  ): MigrationSpec.Custom = MigrationSpec.Custom.create(locations, migrateOnOpen, configure)

  fun withJdbcTemplate(
    spec: MigrationSpec = MigrationSpec.Full,
    block: (JdbcTemplate) -> Unit,
  ) {
    openDatabase(spec).use { lease ->
      block(lease.jdbcTemplate())
    }
  }
}

fun PostgresTestDatabaseLease.jdbcTemplate(): JdbcTemplate {
  val dataSource =
    DriverManagerDataSource(jdbcUrl, username, password).apply {
      setDriverClassName("org.postgresql.Driver")
    }
  return JdbcTemplate(dataSource)
}

fun DynamicPropertyRegistry.registerWorkbenchDataSource(lease: PostgresTestDatabaseLease) {
  add("spring.datasource.url") { lease.jdbcUrl }
  add("spring.datasource.username") { lease.username }
  add("spring.datasource.password") { lease.password }
}
