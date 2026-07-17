package one.ztd.workbench.worker.messaging

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.mockk.mockk
import one.ztd.workbench.application.jobs.messaging.MessagingProperties
import one.ztd.workbench.application.jobs.messaging.MessagingTransport
import org.springframework.boot.ApplicationArguments

class WorkerTransportGuardTest :
  StringSpec({
    val arguments = mockk<ApplicationArguments>()

    "worker accepts kafka transport" {
      WorkerTransportGuard(MessagingProperties(transport = MessagingTransport.KAFKA)).run(arguments)
    }

    "worker rejects embedded transports" {
      shouldThrow<IllegalStateException> {
        WorkerTransportGuard(MessagingProperties()).run(arguments)
      }
    }
  })
