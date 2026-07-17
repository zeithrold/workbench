package one.ztd.workbench.application.jobs.messaging

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MessagingPropertiesTest :
  StringSpec({
    "defaults select compact postgres transport" {
      val properties = MessagingProperties()
      properties.transport shouldBe MessagingTransport.POSTGRES
      properties.batchSize shouldBe 50
      properties.concurrency shouldBe 4
      properties.maxAttempts shouldBe 8
    }

    "numeric limits must be positive" {
      shouldThrow<IllegalArgumentException> { MessagingProperties(batchSize = 0) }
      shouldThrow<IllegalArgumentException> { MessagingProperties(concurrency = 0) }
      shouldThrow<IllegalArgumentException> { MessagingProperties(maxAttempts = 0) }
    }
  })
