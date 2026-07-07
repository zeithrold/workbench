package ink.doa.workbench.agile.testfixtures

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigStatusRecord
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

object AgileWorkItemFixtures {
  fun permissiveCondition(): JsonObject =
    JsonObject(
      mapOf(
        "field" to JsonPrimitive("issue.statusGroup"),
        "op" to JsonPrimitive("eq"),
        "value" to JsonPrimitive("todo"),
      )
    )

  fun sampleConfig(tenantId: UUID): IssueTypeConfigDetails {
    val configId = UUID.randomUUID()
    val statusId = UUID.randomUUID()
    return IssueTypeConfigDetails(
      config =
        IssueTypeConfigRecord(
          id = configId,
          apiId = PublicId.new("itc"),
          tenantId = tenantId,
          scope = WorkItemConfigScope.TENANT,
          projectId = null,
          issueTypeId = UUID.randomUUID(),
          issueTypeApiId = PublicId.new("typ"),
          workflowId = UUID.randomUUID(),
          workflowApiId = PublicId.new("wfl"),
          version = 1,
          nameOverride = null,
          iconOverride = null,
          colorOverride = null,
          rank = 100,
          isActive = true,
          validFrom = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
          validTo = null,
          createdBy = null,
          createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
          createFields = JsonObject(emptyMap()),
        ),
      statuses =
        listOf(
          IssueTypeConfigStatusRecord(
            id = UUID.randomUUID(),
            tenantId = tenantId,
            issueTypeConfigId = configId,
            statusId = statusId,
            statusApiId = PublicId.new("sts"),
            code = "todo",
            name = "Todo",
            statusGroup = WorkItemStatusGroup.TODO,
            isInitial = true,
            isTerminal = false,
            rank = 100,
          )
        ),
      properties = emptyList(),
    )
  }

  fun sampleIssue(
    tenantId: UUID,
    projectId: UUID,
    config: IssueTypeConfigDetails,
    actorId: UUID,
  ): WorkItemRecord {
    val status = config.statuses.single()
    return WorkItemRecord(
      id = UUID.randomUUID(),
      apiId = PublicId.new("iss"),
      tenantId = tenantId,
      projectId = projectId,
      issueTypeApiId = config.config.issueTypeApiId,
      issueTypeConfigApiId = config.config.apiId,
      key = "CORE-1",
      title = "Issue",
      description = null,
      statusId = status.statusId,
      statusApiId = status.statusApiId,
      statusGroup = WorkItemStatusGroup.TODO,
      reporterId = actorId,
      assigneeId = actorId,
      priorityApiId = null,
      reporterApiId = PublicId.new("usr"),
      assigneeApiId = PublicId.new("usr"),
      sprintApiId = null,
      properties = JsonObject(emptyMap()),
      createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
      updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    )
  }

  fun sampleTransition(config: IssueTypeConfigDetails): WorkflowTransitionRecord {
    val status = config.statuses.single()
    return WorkflowTransitionRecord(
      id = UUID.randomUUID(),
      apiId = PublicId.new("trn"),
      tenantId = config.config.tenantId,
      workflowId = config.config.workflowId,
      name = "Done",
      fromStatusId = status.statusId,
      fromStatusApiId = status.statusApiId,
      toStatusId = status.statusId,
      toStatusApiId = status.statusApiId,
      rank = 100,
      preconditionAst = permissiveCondition(),
      fields =
        Json.parseToJsonElement(
            """
            {
              "version": 1,
              "resource": "work_item",
              "target": "transition",
              "fields": {
                "title": { "participation": "optional" }
              }
            }
            """
              .trimIndent()
          )
          .jsonObject,
      isActive = true,
      createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
      updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    )
  }
}
