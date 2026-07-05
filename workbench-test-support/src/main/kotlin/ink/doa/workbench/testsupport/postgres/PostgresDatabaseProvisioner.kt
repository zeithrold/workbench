package ink.doa.workbench.testsupport.postgres

import java.sql.DriverManager
import java.util.UUID
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

internal object PostgresDatabaseProvisioner {
  private val lock = Any()
  private val templateDatabaseNames = mutableMapOf<String, String>()

  fun openDatabase(spec: MigrationSpec, dropOnClose: Boolean): PostgresTestDatabaseLease {
    SharedPostgresContainer.ensureRunning()
    val databaseName =
      synchronized(lock) {
        if (spec.supportsTemplate()) {
          cloneFromTemplate(spec)
        } else {
          createAndMigrate(spec)
        }
      }
    val jdbcUrl = SharedPostgresContainer.databaseJdbcUrl(databaseName)
    val username = SharedPostgresContainer.username
    val password = SharedPostgresContainer.password
    val database =
      Database.connect(
        url = jdbcUrl,
        driver = "org.postgresql.Driver",
        user = username,
        password = password,
      )
    return PostgresTestDatabaseLease(
      database = database,
      jdbcUrl = jdbcUrl,
      username = username,
      password = password,
      databaseName = databaseName,
      dropOnClose = dropOnClose,
    )
  }

  fun dropDatabase(databaseName: String) {
    synchronized(lock) {
      executeStatement("DROP DATABASE IF EXISTS \"$databaseName\" WITH (FORCE)")
    }
  }

  private fun cloneFromTemplate(spec: MigrationSpec): String {
    val templateName = ensureTemplate(spec)
    val databaseName = uniqueDatabaseName("test")
    executeStatement(
      "CREATE DATABASE \"$databaseName\" TEMPLATE \"$templateName\"",
    )
    return databaseName
  }

  private fun createAndMigrate(spec: MigrationSpec): String {
    val databaseName = uniqueDatabaseName("test")
    executeStatement("CREATE DATABASE \"$databaseName\"")
    if (spec is MigrationSpec.Custom && !spec.migrateOnOpen) {
      return databaseName
    }
    migrate(spec, databaseName)
    return databaseName
  }

  private fun ensureTemplate(spec: MigrationSpec): String {
    val key = spec.templateKey()
    templateDatabaseNames[key]?.let { return it }

    val templateName = uniqueDatabaseName("workbench_tpl_${spec.templateKey()}")
    executeStatement("CREATE DATABASE \"$templateName\"")
    migrate(spec, templateName)
    templateDatabaseNames[key] = templateName
    return templateName
  }

  private fun migrate(spec: MigrationSpec, databaseName: String) {
    val flyway =
      spec
        .configureFlyway(
          Flyway.configure()
            .dataSource(
              SharedPostgresContainer.databaseJdbcUrl(databaseName),
              SharedPostgresContainer.username,
              SharedPostgresContainer.password,
            )
            .locations(*spec.locations()),
        )
        .load()
    flyway.migrate()
  }

  private fun executeStatement(sql: String) {
    DriverManager.getConnection(
        SharedPostgresContainer.adminJdbcUrl(),
        SharedPostgresContainer.username,
        SharedPostgresContainer.password,
      )
      .use { connection ->
        connection.createStatement().use { statement ->
          statement.execute(sql)
        }
      }
  }

  private fun uniqueDatabaseName(prefix: String): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "")}"
}
