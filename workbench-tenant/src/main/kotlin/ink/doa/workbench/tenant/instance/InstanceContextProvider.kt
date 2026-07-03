package ink.doa.workbench.tenant.instance

import ink.doa.workbench.core.common.context.InstanceContextSummary
import java.net.InetAddress
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class InstanceContextProvider(
  private val instanceProperties: InstanceProperties,
  @Value("\${spring.application.name:workbench}") private val applicationName: String,
) {
  fun current(): InstanceContextSummary =
    InstanceContextSummary(
      id = instanceProperties.id?.takeIf { it.isNotBlank() } ?: defaultInstanceId(),
      name = instanceProperties.name?.takeIf { it.isNotBlank() } ?: applicationName,
    )

  private fun defaultInstanceId(): String =
    runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("default")
}
