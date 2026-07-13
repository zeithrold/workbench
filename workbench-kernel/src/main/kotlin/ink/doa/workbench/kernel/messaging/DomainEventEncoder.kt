package ink.doa.workbench.kernel.messaging

import java.time.Clock
import java.util.UUID
import kotlinx.serialization.json.Json

class DomainEventEncoder(private val clock: Clock, private val json: Json = defaultJson) {
  fun <T : Any> encode(
    spec: DomainEventSpec<T>,
    payload: T,
    metadata: EventMetadata = EventMetadata(),
  ): String {
    val envelope =
      DomainEventEnvelope(
        eventId = UUID.randomUUID().toString(),
        type = spec.type,
        version = spec.currentVersion,
        occurredAt = clock.instant().toString(),
        traceId = metadata.traceId,
        tenantId = metadata.tenantId,
        payload = json.encodeToJsonElement(spec.serializer, payload),
      )
    return json.encodeToString(envelope)
  }

  companion object {
    val defaultJson: Json = Json {
      encodeDefaults = true
      ignoreUnknownKeys = true
    }
  }
}
