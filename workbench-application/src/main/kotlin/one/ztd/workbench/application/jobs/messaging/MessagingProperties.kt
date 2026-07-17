package one.ztd.workbench.application.jobs.messaging

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("workbench.messaging")
data class MessagingProperties(
  val transport: MessagingTransport = MessagingTransport.POSTGRES,
  val batchSize: Int = 50,
  val concurrency: Int = 4,
  val leaseDuration: Duration = Duration.ofMinutes(2),
  val fallbackPollInterval: Duration = Duration.ofSeconds(5),
  val maxAttempts: Int = 8,
  val retentionCleanupBatchSize: Int = 1000,
  val epoch: String = "local",
  val shutdownTimeout: Duration = Duration.ofSeconds(30),
) {
  init {
    require(batchSize > 0) { "workbench.messaging.batch-size must be positive" }
    require(concurrency > 0) { "workbench.messaging.concurrency must be positive" }
    require(maxAttempts > 0) { "workbench.messaging.max-attempts must be positive" }
    require(retentionCleanupBatchSize > 0) {
      "workbench.messaging.retention-cleanup-batch-size must be positive"
    }
    require(epoch.isNotBlank()) { "workbench.messaging.epoch must not be blank" }
  }
}

enum class MessagingTransport {
  POSTGRES,
  REDIS_STREAMS,
  KAFKA,
}
