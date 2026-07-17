package one.ztd.workbench.worker.messaging

import one.ztd.workbench.application.jobs.messaging.MessagingProperties
import one.ztd.workbench.application.jobs.messaging.MessagingTransport
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
