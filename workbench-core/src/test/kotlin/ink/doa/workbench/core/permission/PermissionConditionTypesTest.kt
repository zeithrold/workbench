package ink.doa.workbench.core.permission

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.access.WorkItemAccessActor
import ink.doa.workbench.core.workitem.access.WorkItemAccessEvaluationContext
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class PermissionConditionTypesTest :
  StringSpec({
    "permission condition context stores actor and attributes" {
      val actorId = UUID.randomUUID()
      val context =
        PermissionConditionContext(
          actorUserId = actorId,
          resourceAttributes = mapOf("assignee" to actorId.toString()),
        )
      context.actorUserId shouldBe actorId
      context.resourceAttributes["assignee"] shouldBe actorId.toString()
    }

    "permission condition result enum values are stable" {
      PermissionConditionResult.entries shouldBe
        listOf(
          PermissionConditionResult.MATCH,
          PermissionConditionResult.NO_MATCH,
          PermissionConditionResult.INVALID,
        )
    }

    "work item access evaluation context carries issue type config id" {
      val actorId = UUID.randomUUID()
      val configId = UUID.randomUUID()
      val workItem =
        WorkItemRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("iss"),
          tenantId = UUID.randomUUID(),
          projectId = UUID.randomUUID(),
          issueTypeApiId = PublicId.new("typ"),
          issueTypeConfigApiId = PublicId.new("itc"),
          key = "CORE-1",
          title = "Issue",
          description = null,
          statusId = UUID.randomUUID(),
          statusApiId = PublicId.new("sts"),
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
      val evaluation =
        WorkItemAccessEvaluationContext(
          actor = WorkItemAccessActor(actorId, emptySet(), emptySet()),
          workItem = workItem,
          issueTypeConfigId = configId,
          properties = emptyMap(),
          childIssuesNotDone = 1,
        )
      evaluation.issueTypeConfigId shouldBe configId
      evaluation.childIssuesNotDone shouldBe 1
    }
  })
