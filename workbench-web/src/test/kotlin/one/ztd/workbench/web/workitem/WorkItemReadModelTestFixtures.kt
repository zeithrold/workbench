package one.ztd.workbench.web.workitem

import one.ztd.workbench.agile.workitem.model.WorkItemIssueTypeSummary
import one.ztd.workbench.agile.workitem.model.WorkItemRecord
import one.ztd.workbench.agile.workitem.model.WorkItemSearchHit
import one.ztd.workbench.agile.workitem.model.WorkItemStatusSummary
import one.ztd.workbench.agile.workitem.model.WorkItemUserSummary

internal fun workItemReadModel(record: WorkItemRecord): WorkItemSearchHit =
  WorkItemSearchHit(
    databaseId = record.id,
    apiId = record.apiId.value,
    key = record.key,
    title = record.title,
    description = record.description,
    projectApiId = "prj_01JABCDEFGHJKMNPQRSTVWXYZ0",
    issueType =
      WorkItemIssueTypeSummary(
        id = record.issueTypeApiId.value,
        code = "task",
        name = "Task",
        icon = null,
        color = null,
      ),
    issueTypeConfigApiId = record.issueTypeConfigApiId.value,
    status =
      WorkItemStatusSummary(
        id = record.statusApiId.value,
        code = record.statusGroup.dbValue,
        name = record.statusGroup.name.lowercase().replaceFirstChar(Char::uppercase),
        group = record.statusGroup.dbValue,
        color = null,
        terminal = false,
      ),
    priority = null,
    reporter = WorkItemUserSummary(record.reporterApiId.value, "Reporter"),
    assignee = record.assigneeApiId?.let { WorkItemUserSummary(it.value, "Assignee") },
    sprint = null,
    createdAt = record.createdAt,
    updatedAt = record.updatedAt,
    properties = emptyMap(),
  )
