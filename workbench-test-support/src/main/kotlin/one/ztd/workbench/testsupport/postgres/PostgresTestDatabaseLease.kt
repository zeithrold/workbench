package one.ztd.workbench.testsupport.postgres

import org.jetbrains.exposed.v1.jdbc.Database

class PostgresTestDatabaseLease
internal constructor(
  val database: Database,
  val jdbcUrl: String,
  val username: String,
  val password: String,
  private val databaseName: String,
  private val dropOnClose: Boolean,
) : AutoCloseable {
  @Volatile private var closed = false

  override fun close() {
    if (!dropOnClose || closed) {
      return
    }
    synchronized(this) {
      if (closed) {
        return
      }
      PostgresDatabaseProvisioner.dropDatabase(databaseName)
      closed = true
    }
  }
}
