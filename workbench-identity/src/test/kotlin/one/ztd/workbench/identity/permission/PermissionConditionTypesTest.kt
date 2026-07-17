package one.ztd.workbench.identity.permission

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import one.ztd.workbench.agile.workitem.access.WorkItemAccessActor
import one.ztd.workbench.agile.workitem.access.WorkItemAccessEvaluationContext
import one.ztd.workbench.agile.workitem.model.WorkItemRecord
import one.ztd.workbench.agile.workitem.model.WorkItemStatusGroup
import one.ztd.workbench.kernel.common.ids.PublicId

class PermissionConditionTypesTest :
  StringSpec({
    "permission condition context stores actor and attributes" {
      val actorApiId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0"
      val context =
        PermissionConditionContext(
          actorUserApiId = actorApiId,
          resourceAttributes = mapOf("assignee" to actorApiId),
        )
      context.actorUserApiId shouldBe actorApiId
      context.resourceAttributes["assignee"] shouldBe actorApiId
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
          actor =
            WorkItemAccessActor(
              userId = actorId,
              userApiId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
              groupIds = emptySet(),
              projectRoles = emptySet(),
            ),
          workItem = workItem,
          issueTypeConfigId = configId,
          projectApiId = "prj_01JABCDEFGHJKMNPQRSTVWXYZ1",
          properties = emptyMap(),
          childIssuesNotDone = 1,
        )
      evaluation.issueTypeConfigId shouldBe configId
      evaluation.childIssuesNotDone shouldBe 1
    }
  })
