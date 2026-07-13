package ink.doa.workbench.worker.messaging

import ink.doa.workbench.application.jobs.messaging.DomainEventExecutionService
import ink.doa.workbench.application.jobs.messaging.MessagingProperties
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class KafkaDeliveryRetryJobsTest :
  StringSpec({
    "retry job drains all full transport batches" {
      val execution = mockk<DomainEventExecutionService>()
      every { execution.drainTransportReady() } returnsMany listOf(2, 2, 1)

      KafkaDeliveryRetryJobs(execution, MessagingProperties(batchSize = 2)).retryReady()

      verify(exactly = 3) { execution.drainTransportReady() }
    }
  })
