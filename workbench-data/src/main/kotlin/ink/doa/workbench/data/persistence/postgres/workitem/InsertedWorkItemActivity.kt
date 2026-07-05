package ink.doa.workbench.data.persistence.postgres.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.activity.CreateWorkItemActivityCommand
import ink.doa.workbench.core.workitem.activity.PendingWorkItemActivity
import ink.doa.workbench.core.workitem.activity.WorkItemActivityCodec
import ink.doa.workbench.core.workitem.activity.WorkItemActivityStatusRef
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

internal data class InsertedWorkItemActivity(
  val id: UUID,
  val apiId: PublicId,
)

internal fun preparePendingWorkItemActivity(
  codec: WorkItemActivityCodec,
  command: CreateWorkItemActivityCommand<*>,
): PendingWorkItemActivity {
  codec.validateRoundTrip(command.spec, command.payload)
  return PendingWorkItemActivity(
    id = UUID.randomUUID(),
    apiId = PublicId.new("act"),
    command = command,
  )
}

internal fun insertWorkItemActivity(
  codec: WorkItemActivityCodec,
  command: CreateWorkItemActivityCommand<*>,
): InsertedWorkItemActivity {
  val pending = preparePendingWorkItemActivity(codec, command)
  return insertWorkItemActivityWithId(codec, pending.command, pending.id, pending.apiId)
}

internal fun insertWorkItemActivityWithId(
  codec: WorkItemActivityCodec,
  command: CreateWorkItemActivityCommand<*>,
  activityId: UUID,
  apiId: PublicId,
): InsertedWorkItemActivity {
  if (
    WorkItemActivitiesTable.selectAll()
      .where { WorkItemActivitiesTable.id eq activityId.toKotlinUuid() }
      .any()
  ) {
    return InsertedWorkItemActivity(id = activityId, apiId = apiId)
  }
  codec.validateRoundTrip(command.spec, command.payload)
  val encoded = codec.encode(command.spec, command.payload)
  val createdAt = command.occurredAt
  WorkItemActivitiesTable.insert {
    it[WorkItemActivitiesTable.id] = activityId.toKotlinUuid()
    it[WorkItemActivitiesTable.apiId] = apiId.value
    it[WorkItemActivitiesTable.tenantId] = command.tenantId.toKotlinUuid()
    it[WorkItemActivitiesTable.projectId] = command.projectId.toKotlinUuid()
    it[WorkItemActivitiesTable.workItemId] = command.workItemId.toKotlinUuid()
    it[WorkItemActivitiesTable.actorUserId] = command.actorUserId?.toKotlinUuid()
    it[WorkItemActivitiesTable.activityType] = command.spec.type.dbValue
    it[WorkItemActivitiesTable.occurredAt] = command.occurredAt
    it[WorkItemActivitiesTable.summary] = command.summary
    it[WorkItemActivitiesTable.payload] = encoded
    it[WorkItemActivitiesTable.sourceType] = command.sourceType.dbValue
    it[WorkItemActivitiesTable.sourceId] = command.sourceId
    it[WorkItemActivitiesTable.correlationId] = command.correlationId
    it[WorkItemActivitiesTable.requestId] = command.requestId
    it[WorkItemActivitiesTable.createdAt] = createdAt
  }
  return InsertedWorkItemActivity(id = activityId, apiId = apiId)
}

internal fun loadStatusRef(statusId: UUID): WorkItemActivityStatusRef {
  val row =
    IssueStatusesTable.selectAll()
      .where { IssueStatusesTable.id eq statusId.toKotlinUuid() }
      .single()
  return WorkItemActivityStatusRef(
    id = row[IssueStatusesTable.apiId],
    name = row[IssueStatusesTable.name],
    group = row[IssueStatusesTable.statusGroup],
  )
}

internal fun loadIssueTypeRef(
  issueTypeId: UUID
): ink.doa.workbench.core.workitem.activity.WorkItemActivityEntityRef {
  val row =
    IssueTypesTable.selectAll().where { IssueTypesTable.id eq issueTypeId.toKotlinUuid() }.single()
  return ink.doa.workbench.core.workitem.activity.WorkItemActivityEntityRef(
    id = row[IssueTypesTable.apiId],
    display = row[IssueTypesTable.name],
  )
}

internal fun loadTransitionRef(
  transitionId: UUID
): ink.doa.workbench.core.workitem.activity.WorkItemActivityEntityRef {
  val row =
    WorkflowTransitionsTable.selectAll()
      .where { WorkflowTransitionsTable.id eq transitionId.toKotlinUuid() }
      .single()
  return ink.doa.workbench.core.workitem.activity.WorkItemActivityEntityRef(
    id = row[WorkflowTransitionsTable.apiId],
    display = row[WorkflowTransitionsTable.name],
  )
}

internal fun loadEntityRefByApiId(
  table: org.jetbrains.exposed.v1.core.Table,
  id: UUID,
): ink.doa.workbench.core.workitem.activity.WorkItemActivityEntityRef {
  val apiIdColumn =
    table.columns.single { it.name == "api_id" } as org.jetbrains.exposed.v1.core.Column<String>
  val nameColumn =
    table.columns.singleOrNull { it.name == "name" }
      as org.jetbrains.exposed.v1.core.Column<String>?
  val idColumn =
    table.columns.single { it.name == "id" }
      as org.jetbrains.exposed.v1.core.Column<kotlin.uuid.Uuid>
  val row = table.selectAll().where { idColumn eq id.toKotlinUuid() }.single()
  return ink.doa.workbench.core.workitem.activity.WorkItemActivityEntityRef(
    id = row[apiIdColumn],
    display = nameColumn?.let { row[it] },
  )
}

internal fun loadUserEntityRef(
  userId: UUID
): ink.doa.workbench.core.workitem.activity.WorkItemActivityEntityRef? {
  val row =
    ink.doa.workbench.data.persistence.postgres.identity.UsersTable.selectAll()
      .where {
        ink.doa.workbench.data.persistence.postgres.identity.UsersTable.id eq userId.toKotlinUuid()
      }
      .singleOrNull() ?: return null
  return ink.doa.workbench.core.workitem.activity.WorkItemActivityEntityRef(
    id = row[ink.doa.workbench.data.persistence.postgres.identity.UsersTable.apiId],
    display = row[ink.doa.workbench.data.persistence.postgres.identity.UsersTable.displayName],
  )
}
