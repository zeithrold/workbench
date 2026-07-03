package doa.ink.workbench.service.instance

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "workbench.instance")
data class InstanceProperties(val setupToken: String? = null)
