package ink.doa.workbench.jobs.messaging

import ink.doa.workbench.core.port.messaging.OutboxRetentionStore
import ink.doa.workbench.jobs.JobsEnabled
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@JobsEnabled
@Component
class OutboxRetentionJob(
  private val store: OutboxRetentionStore,
  private val properties: MessagingProperties,
) {
  @Scheduled(fixedDelayString = "\${workbench.messaging.retention-cleanup-interval:1h}")
  fun cleanup() {
    var deleted: Int
    do {
      deleted =
        store.deleteExpiredTerminal(
          properties.retentionCleanupBatchSize,
          OffsetDateTime.now(ZoneOffset.UTC),
        )
    } while (deleted == properties.retentionCleanupBatchSize)
  }
}
