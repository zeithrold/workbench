package one.ztd.workbench.kernel.messaging

data class EventMetadata(
  val traceId: String? = null,
  val tenantId: String? = null,
)
