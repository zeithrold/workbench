package doa.ink.workbench

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync @EnableKafka @SpringBootApplication class WorkbenchWorkerApplication

fun main(args: Array<String>) {
  runApplication<WorkbenchWorkerApplication>(*args) {
    setAdditionalProfiles("worker")
  }
}
