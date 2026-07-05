package ink.doa.workbench.web.api

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.boot.context.properties.bind.DefaultValue

/**
 * Controls whether the API echoes the W3C traceparent response header for downstream correlation.
 */
@ConfigurationProperties(prefix = "workbench.tracing.echo-traceparent")
data class EchoTraceparentProperties
@ConstructorBinding
constructor(
  /** When false, the traceparent response filter is not registered. */
  @DefaultValue("true") val enabled: Boolean
)
