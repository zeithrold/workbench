package one.ztd.workbench.kernel.messaging

import kotlinx.serialization.json.Json
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.SerializationParseSupport
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode

class DomainEventDecoder(private val json: Json = DomainEventEncoder.defaultJson) {
  fun parseEnvelope(raw: String): DomainEventEnvelope =
    SerializationParseSupport.parseOrThrow(
      { json.decodeFromString<DomainEventEnvelope>(raw) },
      { ex ->
        InvalidRequestException(
          WorkbenchErrorCode.DOMAIN_EVENT_ENVELOPE_INVALID_JSON,
          "Invalid domain event envelope JSON: ${ex.message}",
        )
      },
    )

  fun <T : Any> decode(spec: DomainEventSpec<T>, envelope: DomainEventEnvelope): T {
    if (envelope.type != spec.type) {
      throw InvalidRequestException(
        WorkbenchErrorCode.DOMAIN_EVENT_TYPE_MISMATCH,
        "Domain event type mismatch: expected '${spec.type}', got '${envelope.type}'.",
      )
    }
    if (envelope.version != spec.currentVersion) {
      throw InvalidRequestException(
        WorkbenchErrorCode.DOMAIN_EVENT_VERSION_UNSUPPORTED,
        "Unsupported domain event version: expected ${spec.currentVersion}, got ${envelope.version}.",
      )
    }
    return SerializationParseSupport.parseOrThrow(
      { json.decodeFromJsonElement(spec.serializer, envelope.payload) },
      { ex ->
        InvalidRequestException(
          WorkbenchErrorCode.DOMAIN_EVENT_PAYLOAD_DECODE_FAILED,
          "Domain event payload cannot be decoded as '${spec.type}': ${ex.message}",
        )
      },
    )
  }
}
