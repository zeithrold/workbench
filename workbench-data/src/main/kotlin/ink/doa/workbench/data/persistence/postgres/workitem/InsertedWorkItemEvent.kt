package ink.doa.workbench.data.persistence.postgres.workitem

import ink.doa.workbench.agile.workitem.stream.AppendWorkItemEventCommand
import ink.doa.workbench.agile.workitem.stream.WorkItemEventCodec
import ink.doa.workbench.kernel.common.ids.PublicId
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

internal data class InsertedWorkItemEvent(
  val id: UUID,
  val apiId: PublicId,
  val sequence: Long,
)

internal fun appendWorkItemEvent(
  codec: WorkItemEventCodec,
  command: AppendWorkItemEventCommand<*>,
): InsertedWorkItemEvent {
  codec.validateRoundTrip(command.spec, command.payload)
  val encoded = codec.encode(command.spec, command.payload)
  val eventId = UUID.randomUUID()
  val apiId = PublicId.new("evt")
  val sequence = nextWorkItemEventSequence(command.workItemId)
  val createdAt = command.occurredAt
  WorkItemEventsTable.insert {
    it[WorkItemEventsTable.id] = eventId.toKotlinUuid()
    it[WorkItemEventsTable.apiId] = apiId.value
    it[WorkItemEventsTable.tenantId] = command.tenantId.toKotlinUuid()
    it[WorkItemEventsTable.projectId] = command.projectId.toKotlinUuid()
    it[WorkItemEventsTable.workItemId] = command.workItemId.toKotlinUuid()
    it[WorkItemEventsTable.sequence] = sequence
    it[WorkItemEventsTable.eventType] = command.spec.type.dbValue
    it[WorkItemEventsTable.occurredAt] = command.occurredAt
    it[WorkItemEventsTable.actorUserId] = command.actorUserId?.toKotlinUuid()
    it[WorkItemEventsTable.summary] = command.summary
    it[WorkItemEventsTable.payload] = encoded
    it[WorkItemEventsTable.sourceType] = command.sourceType.dbValue
    it[WorkItemEventsTable.sourceId] = command.sourceId
    it[WorkItemEventsTable.correlationId] = command.correlationId
    it[WorkItemEventsTable.requestId] = command.requestId
    it[WorkItemEventsTable.createdAt] = createdAt
  }
  insertTimelineProjection(
    InsertTimelineProjectionCommand(
      tenantId = command.tenantId,
      projectId = command.projectId,
      workItemId = command.workItemId,
      eventId = eventId,
      sequence = sequence,
      occurredAt = command.occurredAt,
      createdAt = createdAt,
    )
  )
  return InsertedWorkItemEvent(id = eventId, apiId = apiId, sequence = sequence)
}

internal fun nextWorkItemEventSequence(workItemId: UUID): Long {
  val currentMax =
    WorkItemEventsTable.select(WorkItemEventsTable.sequence.max())
      .where { WorkItemEventsTable.workItemId eq workItemId.toKotlinUuid() }
      .singleOrNull()
      ?.getOrNull(WorkItemEventsTable.sequence.max())
  return (currentMax ?: 0L) + 1L
}

internal data class InsertTimelineProjectionCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemId: UUID,
  val eventId: UUID,
  val sequence: Long,
  val occurredAt: OffsetDateTime,
  val createdAt: OffsetDateTime = occurredAt,
)

internal fun insertTimelineProjection(command: InsertTimelineProjectionCommand) {
  val entryId = UUID.randomUUID()
  WorkItemTimelineEntriesTable.insert {
    it[id] = entryId.toKotlinUuid()
    it[WorkItemTimelineEntriesTable.tenantId] = command.tenantId.toKotlinUuid()
    it[WorkItemTimelineEntriesTable.projectId] = command.projectId.toKotlinUuid()
    it[WorkItemTimelineEntriesTable.workItemId] = command.workItemId.toKotlinUuid()
    it[WorkItemTimelineEntriesTable.eventId] = command.eventId.toKotlinUuid()
    it[WorkItemTimelineEntriesTable.sequence] = command.sequence
    it[WorkItemTimelineEntriesTable.occurredAt] = command.occurredAt
    it[WorkItemTimelineEntriesTable.createdAt] = command.createdAt
  }
}

internal fun softDeleteTimelineByCommentSource(
  tenantId: UUID,
  workItemId: UUID,
  commentApiId: String,
  deletedAt: OffsetDateTime,
) {
  val eventIds =
    WorkItemEventsTable.select(WorkItemEventsTable.id)
      .where {
        (WorkItemEventsTable.tenantId eq tenantId.toKotlinUuid()) and
          (WorkItemEventsTable.workItemId eq workItemId.toKotlinUuid()) and
          (WorkItemEventsTable.eventType eq "comment.added") and
          (WorkItemEventsTable.sourceId eq commentApiId)
      }
      .map { it[WorkItemEventsTable.id].toJavaUuid() }
  eventIds.forEach { eventId ->
    softDeleteTimelineByEvent(tenantId, workItemId, eventId, deletedAt)
  }
}

internal fun softDeleteTimelineByEvent(
  tenantId: UUID,
  workItemId: UUID,
  eventId: UUID,
  deletedAt: OffsetDateTime,
) {
  WorkItemTimelineEntriesTable.update({
    (WorkItemTimelineEntriesTable.tenantId eq tenantId.toKotlinUuid()) and
      (WorkItemTimelineEntriesTable.workItemId eq workItemId.toKotlinUuid()) and
      (WorkItemTimelineEntriesTable.eventId eq eventId.toKotlinUuid()) and
      WorkItemTimelineEntriesTable.deletedAt.isNull()
  }) {
    it[WorkItemTimelineEntriesTable.deletedAt] = deletedAt
  }
}
