package ink.doa.workbench.core.workitem.stream

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.SerializationParseSupport
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.activity.WorkItemActivityPayload
import ink.doa.workbench.core.workitem.activity.WorkItemCommentCreatedPayload
import ink.doa.workbench.core.workitem.activity.WorkItemCommentDeletedPayload
import ink.doa.workbench.core.workitem.activity.WorkItemCommentEditedPayload
import ink.doa.workbench.core.workitem.activity.WorkItemCreatedPayload
import ink.doa.workbench.core.workitem.activity.WorkItemStatusChangedPayload
import ink.doa.workbench.core.workitem.activity.WorkItemUpdatedPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class WorkItemEventCodec(private val json: Json = defaultJson) {
  fun <T : Any> encode(spec: WorkItemEventSpec<T>, payload: T): JsonElement =
    json.encodeToJsonElement(spec.serializer, payload)

  fun <T : Any> validateRoundTrip(spec: WorkItemEventSpec<T>, payload: T) {
    val encoded = encode(spec, payload)
    decode(spec.type, encoded)
  }

  fun decode(type: WorkItemEventType, raw: JsonElement): WorkItemActivityPayload {
    val spec = WorkItemEventSpecs.specFor(type) ?: return WorkItemActivityPayload.Unknown(raw)
    return SerializationParseSupport.parseOrThrow(
      {
        when (type) {
          WorkItemEventType.CREATED ->
            WorkItemActivityPayload.Created(
              json.decodeFromJsonElement(WorkItemCreatedPayload.serializer(), raw)
            )
          WorkItemEventType.UPDATED ->
            WorkItemActivityPayload.Updated(
              json.decodeFromJsonElement(WorkItemUpdatedPayload.serializer(), raw)
            )
          WorkItemEventType.STATUS_CHANGED ->
            WorkItemActivityPayload.StatusChanged(
              json.decodeFromJsonElement(WorkItemStatusChangedPayload.serializer(), raw)
            )
          WorkItemEventType.COMMENT_ADDED ->
            WorkItemActivityPayload.CommentAdded(
              json.decodeFromJsonElement(WorkItemCommentCreatedPayload.serializer(), raw)
            )
          WorkItemEventType.COMMENT_EDITED ->
            WorkItemActivityPayload.CommentEdited(
              json.decodeFromJsonElement(WorkItemCommentEditedPayload.serializer(), raw)
            )
          WorkItemEventType.COMMENT_DELETED ->
            WorkItemActivityPayload.CommentDeleted(
              json.decodeFromJsonElement(WorkItemCommentDeletedPayload.serializer(), raw)
            )
          WorkItemEventType.UNKNOWN -> WorkItemActivityPayload.Unknown(raw)
        }
      },
      { ex ->
        InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_ACTIVITY_PAYLOAD_INVALID,
          "Work item event payload cannot be decoded as '${spec.type.dbValue}': ${ex.message}",
        )
      },
    )
  }

  fun encodePayload(payload: WorkItemActivityPayload): JsonElement =
    when (payload) {
      is WorkItemActivityPayload.Created -> encode(WorkItemEventSpecs.Created, payload.value)
      is WorkItemActivityPayload.Updated -> encode(WorkItemEventSpecs.Updated, payload.value)
      is WorkItemActivityPayload.StatusChanged ->
        encode(WorkItemEventSpecs.StatusChanged, payload.value)
      is WorkItemActivityPayload.CommentAdded ->
        encode(WorkItemEventSpecs.CommentAdded, payload.value)
      is WorkItemActivityPayload.CommentEdited ->
        encode(WorkItemEventSpecs.CommentEdited, payload.value)
      is WorkItemActivityPayload.CommentDeleted ->
        encode(WorkItemEventSpecs.CommentDeleted, payload.value)
      is WorkItemActivityPayload.Unknown -> payload.raw
    }

  companion object {
    val defaultJson: Json = Json {
      encodeDefaults = true
      ignoreUnknownKeys = true
    }
  }
}
