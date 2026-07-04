package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.data.persistence.IssueTypeConfigsTable
import ink.doa.workbench.data.persistence.IssueTypesTable
import ink.doa.workbench.data.persistence.IssuesTable
import ink.doa.workbench.data.persistence.PrioritiesTable
import ink.doa.workbench.data.persistence.SprintsTable
import ink.doa.workbench.data.persistence.UsersTable
import java.util.UUID
import kotlin.uuid.toJavaUuid
import org.jetbrains.exposed.v1.core.ResultRow

internal fun requireWorkItem(tenantId: UUID, projectId: UUID, apiId: String): WorkItemRecord =
  requireIssueRow(tenantId, projectId, apiId).toWorkItemRecord()

internal fun ResultRow.toWorkItemRecord(): WorkItemRecord {
  val issueType = requirePublicId(IssueTypesTable, this[IssuesTable.issueTypeId].toJavaUuid())
  val config =
    requirePublicId(IssueTypeConfigsTable, this[IssuesTable.issueTypeConfigId].toJavaUuid())
  val status = requireStatus(this[IssuesTable.statusId].toJavaUuid())
  return WorkItemRecord(
    id = this[IssuesTable.id].toJavaUuid(),
    apiId = PublicId(this[IssuesTable.apiId]),
    tenantId = this[IssuesTable.tenantId].toJavaUuid(),
    projectId = this[IssuesTable.projectId].toJavaUuid(),
    issueTypeApiId = issueType,
    issueTypeConfigApiId = config,
    key = currentKey(this[IssuesTable.id].toJavaUuid()) ?: fallbackKey(this),
    title = this[IssuesTable.title],
    description = this[IssuesTable.description],
    statusId = this[IssuesTable.statusId].toJavaUuid(),
    statusApiId = status.apiId,
    statusGroup = WorkItemStatusGroup.fromDbValue(status.group),
    reporterId = this[IssuesTable.reporterId].toJavaUuid(),
    assigneeId = this[IssuesTable.assigneeId]?.toJavaUuid(),
    priorityApiId =
      this[IssuesTable.priorityId]?.toJavaUuid()?.let { requirePublicId(PrioritiesTable, it) },
    reporterApiId = requirePublicId(UsersTable, this[IssuesTable.reporterId].toJavaUuid()),
    assigneeApiId =
      this[IssuesTable.assigneeId]?.toJavaUuid()?.let { requirePublicId(UsersTable, it) },
    sprintApiId =
      this[IssuesTable.sprintId]?.toJavaUuid()?.let { requirePublicId(SprintsTable, it) },
    properties = this[IssuesTable.propertiesSnapshot].asObject(),
    createdAt = this[IssuesTable.createdAt],
    updatedAt = this[IssuesTable.updatedAt],
  )
}
