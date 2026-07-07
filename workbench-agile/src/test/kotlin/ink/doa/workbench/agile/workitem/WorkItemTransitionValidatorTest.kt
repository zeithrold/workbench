package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.PermissionDeniedException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.access.WorkItemAccessActor
import ink.doa.workbench.core.workitem.access.WorkItemAccessEvaluationContext
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigStatusRecord
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class WorkItemTransitionValidatorTest :
  StringSpec({
    val repository = mockk<WorkItemRepository>()
    val accessPolicy = mockk<WorkItemAccessPolicyEngine>()
    val validator = WorkItemTransitionValidator(repository, accessPolicy)

    "conditionContext includes child issue count from repository" {
      val issue = sampleIssue()
      coEvery {
        repository.countChildrenNotInStatusGroups(issue.tenantId, issue.id, setOf("done"))
      } returns 2

      val context = runBlocking {
        validator.conditionContext(issue, issue.assigneeId!!, emptyMap())
      }

      context.childIssuesNotDone shouldBe 2
    }

    "checkCondition returns enabled when ast evaluates true" {
      val issue = sampleIssue()
      val ast =
        JsonObject(
          mapOf(
            "field" to JsonPrimitive("statusGroup"),
            "op" to JsonPrimitive("eq"),
            "value" to JsonPrimitive("todo"),
          )
        )

      validator
        .checkCondition(
          ast,
          WorkItemConditionContext(issue, issue.assigneeId!!, emptyMap()),
          failedReason = "failed",
          invalidReason = "invalid",
        )
        .enabled shouldBe true
    }

    "checkCondition returns invalid reason when ast is malformed" {
      val issue = sampleIssue()
      val ast = JsonObject(mapOf("field" to JsonPrimitive("statusGroup")))

      val result =
        validator.checkCondition(
          ast,
          WorkItemConditionContext(issue, issue.assigneeId!!, emptyMap()),
          failedReason = "failed",
          invalidReason = "invalid",
        )

      result.enabled shouldBe false
      result.reason shouldBe "invalid"
    }

    "requireTransitionApplicable rejects workflow mismatch" {
      val issue = sampleIssue()
      val config = sampleConfig(workflowId = UUID.randomUUID())
      val transition =
        sampleTransition(
          workflowId = UUID.randomUUID(),
          fromStatusId = issue.statusId,
          toStatusId = config.statuses.single().statusId,
        )

      shouldThrow<InvalidRequestException> {
          validator.requireTransitionApplicable(issue, config, transition)
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_TRANSITION_STATUS_MISMATCH
    }

    "requireTransitionApplicable rejects unavailable target status" {
      val issue = sampleIssue()
      val config = sampleConfig()
      val transition =
        sampleTransition(
          workflowId = config.config.workflowId,
          fromStatusId = issue.statusId,
          toStatusId = UUID.randomUUID(),
        )

      shouldThrow<InvalidRequestException> {
          validator.requireTransitionApplicable(issue, config, transition)
        }
        .errorCode shouldBe WorkbenchErrorCode.WORKFLOW_TRANSITION_STATUS_UNAVAILABLE
    }

    "requireTransitionPermission rejects when access policy denies transition" {
      val issue = sampleIssue()
      val config = sampleConfig()
      val transition = sampleTransition(workflowId = config.config.workflowId)
      val evaluation =
        WorkItemAccessEvaluationContext(
          actor = WorkItemAccessActor(issue.assigneeId!!, emptySet(), emptySet()),
          workItem = issue,
          issueTypeConfigId = config.config.id,
          properties = emptyMap(),
        )
      coEvery {
        accessPolicy.isTransitionPermitted(config.config.id, transition.id, evaluation)
      } returns false

      shouldThrow<PermissionDeniedException> {
          runBlocking {
            validator.requireTransitionPermission(
              config.config.id,
              transition,
              evaluation,
            )
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_TRANSITION_PERMISSION_DENIED
    }

    "requireTransitionPrecondition rejects when precondition fails" {
      val issue = sampleIssue()
      val transition =
        sampleTransition(
          preconditionAst =
            JsonObject(
              mapOf(
                "field" to JsonPrimitive("statusGroup"),
                "op" to JsonPrimitive("eq"),
                "value" to JsonPrimitive("done"),
              )
            )
        )

      shouldThrow<InvalidRequestException> {
          validator.requireTransitionPrecondition(
            transition,
            WorkItemConditionContext(issue, issue.assigneeId!!, emptyMap()),
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_TRANSITION_PRECONDITION_FAILED
    }
  })

private fun sampleIssue(): WorkItemRecord {
  val actorId = UUID.randomUUID()
  return WorkItemRecord(
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
}

private fun sampleConfig(workflowId: UUID = UUID.randomUUID()): IssueTypeConfigDetails {
  val tenantId = UUID.randomUUID()
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
        workflowId = workflowId,
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

private fun sampleTransition(
  workflowId: UUID = UUID.randomUUID(),
  fromStatusId: UUID? = null,
  toStatusId: UUID = UUID.randomUUID(),
  permissionCondition: JsonObject = JsonObject(emptyMap()),
  preconditionAst: JsonObject = JsonObject(emptyMap()),
): WorkflowTransitionRecord =
  WorkflowTransitionRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("trn"),
    tenantId = UUID.randomUUID(),
    workflowId = workflowId,
    name = "Done",
    fromStatusId = fromStatusId,
    fromStatusApiId = null,
    toStatusId = toStatusId,
    toStatusApiId = PublicId.new("sts"),
    rank = 100,
    permissionCondition = permissionCondition,
    preconditionAst = preconditionAst,
    fields = JsonObject(emptyMap()),
    isActive = true,
    createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
  )
