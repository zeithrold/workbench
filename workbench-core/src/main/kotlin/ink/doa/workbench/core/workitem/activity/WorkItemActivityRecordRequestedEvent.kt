package ink.doa.workbench.core.workitem.activity

import ink.doa.workbench.core.common.ids.PublicId
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WorkItemActivityRecordRequestedEvent(
  val activityId: String,
  val activityApiId: String,
  val tenantId: String,
  val projectId: String,
  val workItemId: String,
  val actorUserId: String?,
  val activityType: String,
  val occurredAt: String,
  val summary: String?,
  val payload: JsonElement,
  val sourceType: String,
  val sourceId: String? = null,
  val correlationId: String? = null,
  val requestId: String? = null,
) {
  companion object {
    fun from(
      pending: PendingWorkItemActivity,
      codec: WorkItemActivityCodec,
    ): WorkItemActivityRecordRequestedEvent {
      val command = pending.command
      codec.validateRoundTrip(command.spec, command.payload)
      return WorkItemActivityRecordRequestedEvent(
        activityId = pending.id.toString(),
        activityApiId = pending.apiId.value,
        tenantId = command.tenantId.toString(),
        projectId = command.projectId.toString(),
        workItemId = command.workItemId.toString(),
        actorUserId = command.actorUserId?.toString(),
        activityType = command.spec.type.dbValue,
        occurredAt = command.occurredAt.toString(),
        summary = command.summary,
        payload = codec.encode(command.spec, command.payload),
        sourceType = command.sourceType.dbValue,
        sourceId = command.sourceId,
        correlationId = command.correlationId,
        requestId = command.requestId,
      )
    }

    fun toCommand(
      event: WorkItemActivityRecordRequestedEvent,
      codec: WorkItemActivityCodec,
    ): CreateWorkItemActivityCommand<*> {
      val activityType = WorkItemActivityType.fromDbValue(event.activityType)
      val spec =
        WorkItemActivitySpecs.specFor(activityType)
          ?: throw IllegalArgumentException("Unknown activity type: ${event.activityType}")
      val payload = codec.decode(activityType, event.payload)
      @Suppress("UNCHECKED_CAST")
      return CreateWorkItemActivityCommand(
        tenantId = UUID.fromString(event.tenantId),
        projectId = UUID.fromString(event.projectId),
        workItemId = UUID.fromString(event.workItemId),
        actorUserId = event.actorUserId?.let(UUID::fromString),
        spec = spec as WorkItemActivitySpec<Any>,
        payload =
          when (payload) {
            is WorkItemActivityPayload.Created -> payload.value
            is WorkItemActivityPayload.Updated -> payload.value
            is WorkItemActivityPayload.StatusChanged -> payload.value
            is WorkItemActivityPayload.CommentCreated -> payload.value
            is WorkItemActivityPayload.Unknown ->
              throw IllegalArgumentException("Cannot persist unknown activity payload type")
          },
        occurredAt = java.time.OffsetDateTime.parse(event.occurredAt),
        summary = event.summary,
        sourceType = WorkItemActivitySourceType.fromDbValue(event.sourceType),
        sourceId = event.sourceId,
        correlationId = event.correlationId,
        requestId = event.requestId,
      )
    }

    fun toPending(
      event: WorkItemActivityRecordRequestedEvent,
      codec: WorkItemActivityCodec,
    ): PendingWorkItemActivity =
      PendingWorkItemActivity(
        id = UUID.fromString(event.activityId),
        apiId = PublicId(event.activityApiId),
        command = toCommand(event, codec),
      )
  }
}
