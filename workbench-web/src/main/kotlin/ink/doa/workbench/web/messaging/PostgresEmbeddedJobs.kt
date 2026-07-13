package ink.doa.workbench.web.messaging

import com.zaxxer.hikari.HikariDataSource
import ink.doa.workbench.application.jobs.messaging.DomainEventExecutionService
import ink.doa.workbench.application.jobs.messaging.MessagingProperties
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport
import javax.sql.DataSource
import org.postgresql.PGConnection
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
  name = ["workbench.messaging.transport"],
  havingValue = "postgres",
  matchIfMissing = true,
)
class PostgresEmbeddedJobs(
  private val dataSource: DataSource,
  private val execution: DomainEventExecutionService,
  private val properties: MessagingProperties,
) : SmartLifecycle {
  private val logger = LoggerFactory.getLogger(javaClass)
  private val running = AtomicBoolean(false)
  private val draining = AtomicBoolean(false)
  private var listenerThread: Thread? = null

  @Scheduled(fixedDelayString = "\${workbench.messaging.fallback-poll-interval:5s}")
  fun scheduledDrain() = drain()

  override fun start() {
    if (!running.compareAndSet(false, true)) return
    listenerThread = Thread.ofVirtual().name("workbench-outbox-listener").start(::listen)
    drain()
  }

  override fun stop() {
    running.set(false)
    listenerThread?.interrupt()
    listenerThread = null
  }

  override fun isRunning(): Boolean = running.get()

  private fun listen() {
    while (running.get()) {
      try {
        listenUntilDisconnected()
      } catch (error: InterruptedException) {
        logger.debug("postgres_outbox_listener_interrupted", error)
        Thread.currentThread().interrupt()
      } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        if (running.get()) {
          logger.error("postgres_outbox_listener_failed", error)
          LockSupport.parkNanos(RECONNECT_DELAY_NANOS)
        }
      }
    }
  }

  private fun listenUntilDisconnected() {
    listenerConnection().use { connection ->
      connection.createStatement().use { it.execute("LISTEN workbench_outbox") }
      val postgres = connection.unwrap(PGConnection::class.java)
      drain()
      while (running.get() && !Thread.currentThread().isInterrupted) {
        if (!postgres.getNotifications(5_000).isNullOrEmpty()) drain()
      }
    }
  }

  private fun listenerConnection() =
    (dataSource as? HikariDataSource)?.let { hikari ->
      DriverManager.getConnection(hikari.jdbcUrl, hikari.username, hikari.password)
    } ?: dataSource.connection

  private fun drain() {
    if (!draining.compareAndSet(false, true)) return
    try {
      var claimed: Int
      do {
        claimed = execution.drainReady()
      } while (claimed == properties.batchSize)
    } finally {
      draining.set(false)
    }
  }

  companion object {
    private const val RECONNECT_DELAY_NANOS = 1_000_000_000L
  }
}
