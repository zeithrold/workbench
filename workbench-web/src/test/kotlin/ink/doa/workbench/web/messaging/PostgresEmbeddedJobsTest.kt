package ink.doa.workbench.web.messaging

import ink.doa.workbench.application.jobs.messaging.DomainEventExecutionService
import ink.doa.workbench.application.jobs.messaging.MessagingProperties
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.sql.DataSource

class PostgresEmbeddedJobsTest :
  StringSpec({
    "scheduled drain executes the PostgreSQL delivery ledger" {
      val execution = mockk<DomainEventExecutionService>()
      every { execution.drainReady() } returns 1
      val jobs =
        PostgresEmbeddedJobs(mockk<DataSource>(), execution, MessagingProperties(batchSize = 2))

      jobs.scheduledDrain()

      verify { execution.drainReady() }
    }
  })
