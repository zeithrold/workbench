package one.ztd.workbench.worker

import one.ztd.workbench.application.WorkerApplicationModuleConfiguration
import one.ztd.workbench.data.DataModuleConfiguration
import one.ztd.workbench.worker.messaging.DebeziumProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@EnableAsync
@EnableScheduling
@EnableKafka
@EnableConfigurationProperties(DebeziumProperties::class)
@Import(
  WorkerApplicationModuleConfiguration::class,
  DataModuleConfiguration::class,
)
@SpringBootApplication
class WorkbenchWorkerApplication

fun main(args: Array<String>) {
  runApplication<WorkbenchWorkerApplication>(args = args) {
    setAdditionalProfiles("worker")
  }
}
