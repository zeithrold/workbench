package ink.doa.workbench.core.messaging

data class EventMetadata(
  val traceId: String? = null,
  val tenantId: String? = null,
)
