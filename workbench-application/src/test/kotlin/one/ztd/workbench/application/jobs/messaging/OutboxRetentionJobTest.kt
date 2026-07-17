package one.ztd.workbench.application.jobs.messaging

import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import one.ztd.workbench.application.messaging.OutboxRetentionStore

class OutboxRetentionJobTest :
  StringSpec({
    "cleanup drains full retention batches" {
      val store = mockk<OutboxRetentionStore>()
      every { store.deleteExpiredTerminal(2, any()) } returnsMany listOf(2, 1)

      OutboxRetentionJob(store, MessagingProperties(retentionCleanupBatchSize = 2)).cleanup()

      verify(exactly = 2) { store.deleteExpiredTerminal(2, any()) }
    }
  })
