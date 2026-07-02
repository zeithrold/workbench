package doa.ink.workbench.web

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync
@SpringBootApplication(scanBasePackages = ["doa.ink.workbench"])
class WorkbenchApplication

fun main(args: Array<String>) {
  runApplication<WorkbenchApplication>(*args)
}
