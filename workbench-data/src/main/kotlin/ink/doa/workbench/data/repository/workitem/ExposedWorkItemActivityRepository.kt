package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.WorkItemActivityRepository
import ink.doa.workbench.core.workitem.activity.CreateWorkItemActivityCommand
import ink.doa.workbench.core.workitem.activity.PendingWorkItemActivity
import ink.doa.workbench.core.workitem.activity.WorkItemActivityCodec
import ink.doa.workbench.core.workitem.activity.WorkItemActivityRecord
import ink.doa.workbench.core.workitem.activity.WorkItemActivityType
import ink.doa.workbench.data.persistence.postgres.identity.UsersTable
import ink.doa.workbench.data.persistence.postgres.workitem.IssuesTable
import ink.doa.workbench.data.persistence.postgres.workitem.WorkItemActivitiesTable
import ink.doa.workbench.data.persistence.postgres.workitem.insertWorkItemActivity
import ink.doa.workbench.data.persistence.postgres.workitem.insertWorkItemActivityWithId
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.springframework.stereotype.Repository

@Repository
class ExposedWorkItemActivityRepository(
  private val database: Database,
  private val codec: WorkItemActivityCodec,
) : WorkItemActivityRepository {
  override suspend fun <T : Any> create(
    command: CreateWorkItemActivityCommand<T>
  ): WorkItemActivityRecord =
    suspendTransaction(db = database) {
      val inserted = insertWorkItemActivity(codec, command)
      loadRecord(inserted.id)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)
    }

  override suspend fun createWithId(pending: PendingWorkItemActivity): WorkItemActivityRecord =
    suspendTransaction(db = database) {
      insertWorkItemActivityWithId(codec, pending.command, pending.id, pending.apiId)
      loadRecord(pending.id)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)
    }

  override suspend fun createAll(
    commands: List<CreateWorkItemActivityCommand<*>>
  ): List<WorkItemActivityRecord> =
    suspendTransaction(db = database) {
      commands.map { command ->
        val inserted = insertWorkItemActivity(codec, command)
        loadRecord(inserted.id)
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)
      }
    }

  internal fun loadRecord(activityId: UUID): WorkItemActivityRecord? =
    WorkItemActivitiesTable.join(UsersTable, JoinType.LEFT) {
        WorkItemActivitiesTable.actorUserId eq UsersTable.id
      }
      .selectAll()
      .where { WorkItemActivitiesTable.id eq activityId.toKotlinUuid() }
      .singleOrNull()
      ?.toRecord()

  private fun ResultRow.toRecord(): WorkItemActivityRecord {
    val activityType = WorkItemActivityType.fromDbValue(this[WorkItemActivitiesTable.activityType])
    val payload = codec.decode(activityType, this[WorkItemActivitiesTable.payload])
    val workItemId = this[WorkItemActivitiesTable.workItemId].toJavaUuid()
    val workItemApiId =
      IssuesTable.selectAll()
        .where { IssuesTable.id eq workItemId.toKotlinUuid() }
        .single()[IssuesTable.apiId]
        .let(::PublicId)
    val actorUserId = this.getOrNull(WorkItemActivitiesTable.actorUserId)?.toJavaUuid()
    return WorkItemActivityRecord(
      id = this[WorkItemActivitiesTable.id].toJavaUuid(),
      apiId = PublicId(this[WorkItemActivitiesTable.apiId]),
      tenantId = this[WorkItemActivitiesTable.tenantId].toJavaUuid(),
      projectId = this[WorkItemActivitiesTable.projectId].toJavaUuid(),
      workItemId = workItemId,
      workItemApiId = workItemApiId,
      actorUserId = actorUserId,
      actorApiId = actorUserId?.let { loadActorApiId(it) },
      actorDisplayName = this.getOrNull(UsersTable.displayName),
      activityType = activityType,
      occurredAt = this[WorkItemActivitiesTable.occurredAt],
      summary = this[WorkItemActivitiesTable.summary],
      payload = payload,
      sourceType =
        ink.doa.workbench.core.workitem.activity.WorkItemActivitySourceType.fromDbValue(
          this[WorkItemActivitiesTable.sourceType]
        ),
      createdAt = this[WorkItemActivitiesTable.createdAt],
    )
  }

  private fun loadActorApiId(userId: UUID): PublicId? =
    UsersTable.selectAll()
      .where { UsersTable.id eq userId.toKotlinUuid() }
      .singleOrNull()
      ?.get(UsersTable.apiId)
      ?.let(::PublicId)
}
