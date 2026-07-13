package ink.doa.workbench.web

import ink.doa.workbench.application.ApplicationModuleConfiguration
import ink.doa.workbench.data.DataModuleConfiguration
import ink.doa.workbench.security.SecurityModuleConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@EnableAsync
@EnableScheduling
@Import(
  ApplicationModuleConfiguration::class,
  DataModuleConfiguration::class,
  SecurityModuleConfiguration::class,
)
@SpringBootApplication
class WorkbenchApplication

fun main(args: Array<String>) {
  runApplication<WorkbenchApplication>(args = args) {
    setAdditionalProfiles("web")
  }
}
