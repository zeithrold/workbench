package one.ztd.workbench.web

import one.ztd.workbench.application.ApplicationModuleConfiguration
import one.ztd.workbench.data.DataModuleConfiguration
import one.ztd.workbench.security.SecurityModuleConfiguration
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
