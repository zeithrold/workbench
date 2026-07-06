package ink.doa.workbench.web

import ink.doa.workbench.security.invitation.InvitationLinkProperties
import ink.doa.workbench.tenant.instance.InstanceProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync
@EnableConfigurationProperties(InstanceProperties::class, InvitationLinkProperties::class)
@SpringBootApplication(scanBasePackages = ["ink.doa.workbench"])
class WorkbenchApplication

fun main(args: Array<String>) {
  runApplication<WorkbenchApplication>(args = args) {
    setAdditionalProfiles("web")
  }
}
