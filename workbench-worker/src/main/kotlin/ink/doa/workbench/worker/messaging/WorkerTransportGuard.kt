package ink.doa.workbench.worker.messaging

import ink.doa.workbench.jobs.messaging.MessagingProperties
import ink.doa.workbench.jobs.messaging.MessagingTransport
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class WorkerTransportGuard(private val properties: MessagingProperties) : ApplicationRunner {
  override fun run(args: ApplicationArguments) {
    check(properties.transport == MessagingTransport.KAFKA) {
      "workbench-worker only supports workbench.messaging.transport=kafka"
    }
  }
}
