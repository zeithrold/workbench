package ink.doa.workbench.testsupport.postgres

import org.testcontainers.containers.PostgreSQLContainer

internal object SharedPostgresContainer {
  private val container: PostgreSQLContainer<*> =
    PostgreSQLContainer("postgres:18-alpine")

  @Volatile private var started = false

  init {
    Runtime.getRuntime().addShutdownHook(Thread { stopIfRunning() })
  }

  val username: String
    get() = container.username

  val password: String
    get() = container.password

  fun container(): PostgreSQLContainer<*> {
    ensureRunning()
    return container
  }

  fun adminJdbcUrl(): String = databaseJdbcUrl("postgres")

  fun databaseJdbcUrl(databaseName: String): String {
    ensureRunning()
    val base = container.jdbcUrl.substringBeforeLast('/')
    return "$base/$databaseName"
  }

  fun ensureRunning() {
    if (started) {
      return
    }
    synchronized(this) {
      if (!started) {
        container.start()
        started = true
      }
    }
  }

  private fun stopIfRunning() {
    synchronized(this) {
      if (started) {
        container.stop()
        started = false
      }
    }
  }
}
