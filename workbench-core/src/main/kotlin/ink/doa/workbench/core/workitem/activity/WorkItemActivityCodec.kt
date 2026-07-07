package ink.doa.workbench.core.workitem.activity

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.SerializationParseSupport
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class WorkItemActivityCodec(private val json: Json = defaultJson) {
  fun <T : Any> encode(spec: WorkItemActivitySpec<T>, payload: T): JsonElement =
    json.encodeToJsonElement(spec.serializer, payload)

  fun <T : Any> validateRoundTrip(spec: WorkItemActivitySpec<T>, payload: T) {
    val encoded = encode(spec, payload)
    decode(spec.type, encoded)
  }

  fun decode(type: WorkItemActivityType, raw: JsonElement): WorkItemActivityPayload {
    val spec = WorkItemActivitySpecs.specFor(type) ?: return WorkItemActivityPayload.Unknown(raw)
    return SerializationParseSupport.parseOrThrow(
      {
        when (type) {
          WorkItemActivityType.CREATED ->
            WorkItemActivityPayload.Created(
              json.decodeFromJsonElement(WorkItemCreatedPayload.serializer(), raw)
            )
          WorkItemActivityType.UPDATED ->
            WorkItemActivityPayload.Updated(
              json.decodeFromJsonElement(WorkItemUpdatedPayload.serializer(), raw)
            )
          WorkItemActivityType.STATUS_CHANGED ->
            WorkItemActivityPayload.StatusChanged(
              json.decodeFromJsonElement(WorkItemStatusChangedPayload.serializer(), raw)
            )
          WorkItemActivityType.COMMENT_CREATED ->
            WorkItemActivityPayload.CommentCreated(
              json.decodeFromJsonElement(WorkItemCommentCreatedPayload.serializer(), raw)
            )
          WorkItemActivityType.UNKNOWN -> WorkItemActivityPayload.Unknown(raw)
        }
      },
      { ex ->
        InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_ACTIVITY_PAYLOAD_INVALID,
          "Work item activity payload cannot be decoded as '${spec.type.dbValue}': ${ex.message}",
        )
      },
    )
  }

  fun encodePayload(payload: WorkItemActivityPayload): JsonElement =
    when (payload) {
      is WorkItemActivityPayload.Created -> encode(WorkItemActivitySpecs.Created, payload.value)
      is WorkItemActivityPayload.Updated -> encode(WorkItemActivitySpecs.Updated, payload.value)
      is WorkItemActivityPayload.StatusChanged ->
        encode(WorkItemActivitySpecs.StatusChanged, payload.value)
      is WorkItemActivityPayload.CommentCreated ->
        encode(WorkItemActivitySpecs.CommentCreated, payload.value)
      is WorkItemActivityPayload.CommentAdded ->
        encode(WorkItemActivitySpecs.CommentCreated, payload.value)
      is WorkItemActivityPayload.CommentEdited ->
        json.encodeToJsonElement(
          WorkItemCommentEditedPayload.serializer(),
          payload.value,
        )
      is WorkItemActivityPayload.CommentDeleted ->
        json.encodeToJsonElement(
          WorkItemCommentDeletedPayload.serializer(),
          payload.value,
        )
      is WorkItemActivityPayload.Unknown -> payload.raw
    }

  companion object {
    val defaultJson: Json = Json {
      encodeDefaults = true
      ignoreUnknownKeys = true
    }
  }
}
