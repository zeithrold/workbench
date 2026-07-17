package one.ztd.workbench.worker.messaging

import one.ztd.workbench.application.jobs.messaging.DomainEventExecutionService
import one.ztd.workbench.application.jobs.messaging.MessagingProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["workbench.messaging.transport"], havingValue = "kafka")
class KafkaDeliveryRetryJobs(
  private val execution: DomainEventExecutionService,
  private val properties: MessagingProperties,
) {
  @Scheduled(fixedDelayString = "\${workbench.messaging.fallback-poll-interval:5s}")
  fun retryReady() {
    var claimed: Int
    do {
      claimed = execution.drainTransportReady()
    } while (claimed == properties.batchSize)
  }
}
