package ink.doa.workbench.data.persistence.postgres.workitem

import ink.doa.workbench.core.workitem.activity.WorkItemActivityEntityRef
import ink.doa.workbench.core.workitem.activity.WorkItemActivityStatusRef
import ink.doa.workbench.data.persistence.postgres.findColumn
import ink.doa.workbench.data.persistence.postgres.requireColumn
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

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

internal fun loadIssueTypeRef(issueTypeId: UUID): WorkItemActivityEntityRef {
  val row =
    IssueTypesTable.selectAll().where { IssueTypesTable.id eq issueTypeId.toKotlinUuid() }.single()
  return WorkItemActivityEntityRef(
    id = row[IssueTypesTable.apiId],
    display = row[IssueTypesTable.name],
  )
}

internal fun loadTransitionRef(transitionId: UUID): WorkItemActivityEntityRef {
  val row =
    WorkflowTransitionsTable.selectAll()
      .where { WorkflowTransitionsTable.id eq transitionId.toKotlinUuid() }
      .single()
  return WorkItemActivityEntityRef(
    id = row[WorkflowTransitionsTable.apiId],
    display = row[WorkflowTransitionsTable.name],
  )
}

internal fun loadEntityRefByApiId(
  table: org.jetbrains.exposed.v1.core.Table,
  id: UUID,
): WorkItemActivityEntityRef {
  val apiIdColumn = table.requireColumn<String>("api_id")
  val nameColumn = table.findColumn<String>("name")
  val idColumn = table.requireColumn<kotlin.uuid.Uuid>("id")
  val row = table.selectAll().where { idColumn eq id.toKotlinUuid() }.single()
  return WorkItemActivityEntityRef(
    id = row[apiIdColumn],
    display = nameColumn?.let { row[it] },
  )
}

internal fun loadUserEntityRef(userId: UUID): WorkItemActivityEntityRef? {
  val row =
    ink.doa.workbench.data.persistence.postgres.identity.UsersTable.selectAll()
      .where {
        ink.doa.workbench.data.persistence.postgres.identity.UsersTable.id eq userId.toKotlinUuid()
      }
      .singleOrNull() ?: return null
  return WorkItemActivityEntityRef(
    id = row[ink.doa.workbench.data.persistence.postgres.identity.UsersTable.apiId],
    display = row[ink.doa.workbench.data.persistence.postgres.identity.UsersTable.displayName],
  )
}
