package ink.doa.workbench.web.api

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "workbench.tracing.echo-traceparent")
data class EchoTraceparentProperties(val enabled: Boolean = true)
