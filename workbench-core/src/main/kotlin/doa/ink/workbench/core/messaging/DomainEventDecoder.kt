@file:Suppress("SwallowedException", "ThrowsCount")

package doa.ink.workbench.core.messaging

import doa.ink.workbench.core.common.errors.InvalidRequestException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

class DomainEventDecoder(private val json: Json = DomainEventEncoder.defaultJson) {
  fun parseEnvelope(raw: String): DomainEventEnvelope =
    try {
      json.decodeFromString<DomainEventEnvelope>(raw)
    } catch (ex: SerializationException) {
      throw InvalidRequestException("Invalid domain event envelope JSON: ${ex.message}")
    }

  fun <T : Any> decode(spec: DomainEventSpec<T>, envelope: DomainEventEnvelope): T {
    if (envelope.type != spec.type) {
      throw InvalidRequestException(
        "Domain event type mismatch: expected '${spec.type}', got '${envelope.type}'."
      )
    }
    if (envelope.version != spec.currentVersion) {
      throw InvalidRequestException(
        "Unsupported domain event version: expected ${spec.currentVersion}, got ${envelope.version}."
      )
    }
    return try {
      json.decodeFromJsonElement(spec.serializer, envelope.payload)
    } catch (ex: SerializationException) {
      throw InvalidRequestException(
        "Domain event payload cannot be decoded as '${spec.type}': ${ex.message}"
      )
    }
  }
}
