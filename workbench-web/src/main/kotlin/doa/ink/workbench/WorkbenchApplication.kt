package doa.ink.workbench

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync @SpringBootApplication class WorkbenchApplication

fun main(args: Array<String>) {
  runApplication<WorkbenchApplication>(*args)
}
