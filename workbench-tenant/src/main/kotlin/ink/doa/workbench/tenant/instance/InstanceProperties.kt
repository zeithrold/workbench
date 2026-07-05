package ink.doa.workbench.tenant.instance

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

/** Instance identity and bootstrap settings for a single Workbench deployment. */
@ConfigurationProperties(prefix = "workbench.instance")
data class InstanceProperties
@ConstructorBinding
constructor(
  /** Secret required to bootstrap the first instance admin when the instance is uninitialized. */
  val setupToken: String?,
  /** Stable public identifier for this Workbench instance. */
  val id: String?,
  /** Human-readable display name for this Workbench instance. */
  val name: String?,
)
