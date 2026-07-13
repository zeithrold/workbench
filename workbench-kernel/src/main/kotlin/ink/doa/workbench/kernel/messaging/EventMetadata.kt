package ink.doa.workbench.kernel.messaging

data class EventMetadata(
  val traceId: String? = null,
  val tenantId: String? = null,
)
