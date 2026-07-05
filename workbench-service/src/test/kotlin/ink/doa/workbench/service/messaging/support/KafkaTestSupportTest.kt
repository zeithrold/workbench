package ink.doa.workbench.service.messaging.support

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class KafkaTestSupportTest :
  StringSpec({
    "awaitCondition returns when predicate becomes true" {
      runBlocking {
        var ready = false
        launch {
          delay(50)
          ready = true
        }

        KafkaTestSupport.awaitCondition(timeout = Duration.ofSeconds(1)) { ready }
      }
    }

    "awaitCondition throws when predicate never becomes true" {
      runBlocking {
        shouldThrow<IllegalStateException> {
          KafkaTestSupport.awaitCondition(
            timeout = Duration.ofMillis(200),
            poll = Duration.ofMillis(50),
          ) {
            false
          }
        }
      }
    }
  })
