package ink.doa.workbench.worker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync
@EnableKafka
@SpringBootApplication(scanBasePackages = ["ink.doa.workbench"])
class WorkbenchWorkerApplication

fun main(args: Array<String>) {
  runApplication<WorkbenchWorkerApplication>(*args) {
    setAdditionalProfiles("worker")
  }
}
