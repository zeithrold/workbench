package one.ztd.workbench.web.messaging

import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.sql.DataSource
import one.ztd.workbench.application.jobs.messaging.DomainEventExecutionService
import one.ztd.workbench.application.jobs.messaging.MessagingProperties

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
