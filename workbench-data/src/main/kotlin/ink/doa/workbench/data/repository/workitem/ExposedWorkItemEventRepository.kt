package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.agile.workitem.stream.AppendWorkItemEventCommand
import ink.doa.workbench.agile.workitem.stream.WorkItemEventCodec
import ink.doa.workbench.agile.workitem.stream.WorkItemEventRecord
import ink.doa.workbench.agile.workitem.stream.WorkItemEventRepository
import ink.doa.workbench.agile.workitem.stream.WorkItemEventSourceType
import ink.doa.workbench.agile.workitem.stream.WorkItemEventType
import ink.doa.workbench.data.persistence.postgres.identity.UsersTable
import ink.doa.workbench.data.persistence.postgres.workitem.IssuesTable
import ink.doa.workbench.data.persistence.postgres.workitem.WorkItemEventsTable
import ink.doa.workbench.data.persistence.postgres.workitem.appendWorkItemEvent
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.kernel.common.ids.PublicId
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.springframework.stereotype.Repository

@Repository
class ExposedWorkItemEventRepository(
  private val database: Database,
  private val codec: WorkItemEventCodec,
) : WorkItemEventRepository {
  override suspend fun <T : Any> append(
    command: AppendWorkItemEventCommand<T>
  ): WorkItemEventRecord =
    suspendTransaction(db = database) {
      val inserted = appendWorkItemEvent(codec, command)
      loadRecord(inserted.id)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)
    }

  internal fun loadRecord(eventId: UUID): WorkItemEventRecord? =
    WorkItemEventsTable.join(UsersTable, JoinType.LEFT) {
        WorkItemEventsTable.actorUserId eq UsersTable.id
      }
      .selectAll()
      .where { WorkItemEventsTable.id eq eventId.toKotlinUuid() }
      .singleOrNull()
      ?.toRecord()

  internal fun loadRecordByApiId(tenantId: UUID, eventApiId: String): WorkItemEventRecord? =
    WorkItemEventsTable.join(UsersTable, JoinType.LEFT) {
        WorkItemEventsTable.actorUserId eq UsersTable.id
      }
      .selectAll()
      .where {
        (WorkItemEventsTable.tenantId eq tenantId.toKotlinUuid()) and
          (WorkItemEventsTable.apiId eq eventApiId)
      }
      .singleOrNull()
      ?.toRecord()

  private fun ResultRow.toRecord(): WorkItemEventRecord {
    val eventType = WorkItemEventType.fromDbValue(this[WorkItemEventsTable.eventType])
    val payload = codec.decode(eventType, this[WorkItemEventsTable.payload])
    val workItemId = this[WorkItemEventsTable.workItemId].toJavaUuid()
    val workItemApiId =
      IssuesTable.selectAll()
        .where { IssuesTable.id eq workItemId.toKotlinUuid() }
        .single()[IssuesTable.apiId]
        .let(::PublicId)
    val actorUserId = this.getOrNull(WorkItemEventsTable.actorUserId)?.toJavaUuid()
    return WorkItemEventRecord(
      id = this[WorkItemEventsTable.id].toJavaUuid(),
      apiId = PublicId(this[WorkItemEventsTable.apiId]),
      tenantId = this[WorkItemEventsTable.tenantId].toJavaUuid(),
      projectId = this[WorkItemEventsTable.projectId].toJavaUuid(),
      workItemId = workItemId,
      workItemApiId = workItemApiId,
      sequence = this[WorkItemEventsTable.sequence],
      eventType = eventType,
      actorUserId = actorUserId,
      actorApiId = actorUserId?.let { loadActorApiId(it) },
      actorDisplayName = this.getOrNull(UsersTable.displayName),
      occurredAt = this[WorkItemEventsTable.occurredAt],
      summary = this[WorkItemEventsTable.summary],
      payload = payload,
      sourceType = WorkItemEventSourceType.fromDbValue(this[WorkItemEventsTable.sourceType]),
      createdAt = this[WorkItemEventsTable.createdAt],
    )
  }

  private fun loadActorApiId(userId: UUID): PublicId? =
    UsersTable.selectAll()
      .where { UsersTable.id eq userId.toKotlinUuid() }
      .singleOrNull()
      ?.get(UsersTable.apiId)
      ?.let(::PublicId)
}
