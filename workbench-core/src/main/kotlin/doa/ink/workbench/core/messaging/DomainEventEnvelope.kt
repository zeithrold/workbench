package doa.ink.workbench.core.messaging

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DomainEventEnvelope(
  val eventId: String,
  val type: String,
  val version: Int = 1,
  val occurredAt: String,
  val traceId: String? = null,
  val tenantId: String? = null,
  val payload: JsonElement,
)
