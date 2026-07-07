package ink.doa.workbench.core.workitem.model

import ink.doa.workbench.core.common.ids.PublicId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class WorkItemRecordsTest :
  StringSpec({
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val userId = UUID.randomUUID()

    "work item record stores status and type references" {
      val record =
        WorkItemRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("iss"),
          tenantId = tenantId,
          projectId = projectId,
          issueTypeApiId = PublicId.new("typ"),
          issueTypeConfigApiId = PublicId.new("itc"),
          key = "WB-1",
          title = "First issue",
          description = "Details",
          statusId = UUID.randomUUID(),
          statusApiId = PublicId.new("sts"),
          statusGroup = WorkItemStatusGroup.TODO,
          reporterId = userId,
          assigneeId = null,
          priorityApiId = null,
          reporterApiId = PublicId.new("usr"),
          assigneeApiId = null,
          sprintApiId = null,
          properties = JsonObject(emptyMap()),
          createdAt = now,
          updatedAt = now,
        )

      record.key shouldBe "WB-1"
      record.title shouldBe "First issue"
    }

    "effective issue type config stores resolved scope" {
      val config =
        IssueTypeConfigDetails(
          config =
            IssueTypeConfigRecord(
              id = UUID.randomUUID(),
              apiId = PublicId.new("itc"),
              tenantId = tenantId,
              scope = WorkItemConfigScope.PROJECT,
              projectId = projectId,
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
              validFrom = now,
              validTo = null,
              createdBy = null,
              createdAt = now,
              updatedAt = now,
              createFields = JsonObject(emptyMap()),
            ),
          statuses = emptyList(),
          properties = emptyList(),
        )

      EffectiveIssueTypeConfig(config = config, resolvedFrom = WorkItemConfigScope.PROJECT)
        .resolvedFrom shouldBe WorkItemConfigScope.PROJECT
    }

    "workflow transition command stores optional from status" {
      CreateWorkflowTransitionCommand(
          tenantId = tenantId,
          workflowApiId = "wfl_abc",
          name = "Start",
          fromStatusApiId = null,
          toStatusApiId = "sts_done",
        )
        .toStatusApiId shouldBe "sts_done"
    }

    "work item response maps from record" {
      val record =
        WorkItemRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("wki"),
          tenantId = tenantId,
          projectId = projectId,
          issueTypeApiId = PublicId.new("typ"),
          issueTypeConfigApiId = PublicId.new("itc"),
          key = "WB-9",
          title = "Searchable",
          description = null,
          statusId = UUID.randomUUID(),
          statusApiId = PublicId.new("sts"),
          statusGroup = WorkItemStatusGroup.DONE,
          reporterId = userId,
          assigneeId = null,
          priorityApiId = null,
          reporterApiId = PublicId.new("usr"),
          assigneeApiId = null,
          sprintApiId = null,
          properties = JsonObject(emptyMap()),
          createdAt = now,
          updatedAt = now,
        )

      WorkItemResponse.from(record).statusGroup shouldBe "done"
    }

    "search and mutation models carry paging metadata" {
      val hit =
        WorkItemSearchHit(
          apiId = "wki_abc",
          key = "WB-1",
          title = "Hit",
          description = null,
          projectApiId = "prj_abc",
          issueTypeApiId = "typ_abc",
          issueTypeConfigApiId = "itc_abc",
          statusApiId = "sts_abc",
          statusGroup = "todo",
          priorityApiId = null,
          reporterApiId = "usr_abc",
          assigneeApiId = null,
          sprintApiId = null,
          createdAt = now,
          updatedAt = now,
          properties = JsonObject(emptyMap()),
        )
      WorkItemSearchResult(hits = listOf(hit), nextCursor = null).hits.single().key shouldBe "WB-1"
    }

    "comment and transition commands store actor metadata" {
      TransitionRequest(
          tenantId = tenantId,
          projectId = projectId,
          workItemApiId = "wki_abc",
          transitionApiId = "trn_abc",
          actorUserId = userId,
          actorUserApiId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
        )
        .transitionApiId shouldBe "trn_abc"

      CreateWorkItemCommentCommand(
          tenantId = tenantId,
          projectId = projectId,
          workItemApiId = "wki_abc",
          authorId = userId,
          body = "Looks good",
        )
        .body shouldBe "Looks good"
    }
  })
