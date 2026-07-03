package doa.ink.workbench.web

import doa.ink.workbench.service.instance.InstanceProperties
import doa.ink.workbench.service.invitation.InvitationLinkProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync
@EnableConfigurationProperties(InstanceProperties::class, InvitationLinkProperties::class)
@SpringBootApplication(scanBasePackages = ["doa.ink.workbench"])
class WorkbenchApplication

fun main(args: Array<String>) {
  runApplication<WorkbenchApplication>(*args)
}
