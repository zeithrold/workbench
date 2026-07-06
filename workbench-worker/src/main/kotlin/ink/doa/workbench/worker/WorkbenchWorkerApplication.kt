package ink.doa.workbench.worker

import ink.doa.workbench.security.invitation.InvitationLinkProperties
import ink.doa.workbench.tenant.instance.InstanceProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync
@EnableKafka
@EnableConfigurationProperties(InstanceProperties::class, InvitationLinkProperties::class)
@SpringBootApplication(scanBasePackages = ["ink.doa.workbench"])
class WorkbenchWorkerApplication

fun main(args: Array<String>) {
  runApplication<WorkbenchWorkerApplication>(args = args) {
    setAdditionalProfiles("worker")
  }
}
