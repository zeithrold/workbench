package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.PermissionDeniedException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemCommentRepository
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.access.WorkItemAccessActor
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommentCommand
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.WorkItemCommentCreateResult
import ink.doa.workbench.core.workitem.model.WorkItemCommentRecord
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class WorkItemCommentServiceTest :
  StringSpec({
    val clock = Clock.fixed(Instant.parse("2026-07-04T10:15:30Z"), ZoneOffset.UTC)
    val comments = mockk<WorkItemCommentRepository>()
    val accessPolicy = mockk<WorkItemAccessPolicyEngine>(relaxed = true)
    val repository = mockk<WorkItemRepository>()
    val configs = mockk<IssueTypeConfigRepository>()
    val service = WorkItemCommentService(comments, accessPolicy, repository, configs)

    fun allowComments() {
      coEvery { accessPolicy.isCommentPermitted(any(), any()) } returns true
      listOf("issue.comment.create", "issue.comment.update", "issue.comment.delete").forEach {
        action ->
        coEvery {
          accessPolicy.bindingAllowsComment(any(), any(), any(), AuthorizationAction(action))
        } returns true
      }
      coEvery { accessPolicy.resolveActor(any(), any(), any()) } returns
        WorkItemAccessActor(
          userId = UUID.randomUUID(),
          userApiId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
          groupIds = emptySet(),
          projectRoles = emptySet(),
        )
    }

    fun denyComments(action: AuthorizationAction) {
      coEvery {
        accessPolicy.bindingAllowsComment(any(), any(), any(), action)
      } returns false
    }

    fun stubWorkItemAccess(tenantId: UUID, projectId: UUID, workItemApiId: String) {
      val issue = sampleIssue(tenantId, projectId, workItemApiId)
      val config = sampleConfig(tenantId, issue.issueTypeConfigApiId.value)
      coEvery { repository.findByApiId(tenantId, projectId, workItemApiId) } returns issue
      coEvery { repository.listPropertyValues(tenantId, issue.id) } returns emptyMap()
      coEvery { repository.resolveProjectApiId(tenantId, projectId) } returns PublicId.new("prj")
      coEvery { configs.findConfig(tenantId, issue.issueTypeConfigApiId.value) } returns config
    }

    val workItemApiId = PublicId.new("iss").value

    "create converts plain text body to html and persists comment" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val issueId = UUID.randomUUID()
      val authorId = UUID.randomUUID()
      allowComments()
      stubWorkItemAccess(tenantId, projectId, workItemApiId)
      coEvery { comments.resolveIssueId(tenantId, projectId, workItemApiId) } returns issueId
      coEvery { comments.create(any(), issueId) } answers
        {
          val command = firstArg<CreateWorkItemCommentCommand>()
          WorkItemCommentCreateResult(
            record =
              WorkItemCommentRecord(
                id = UUID.randomUUID(),
                apiId = PublicId.new("icm"),
                tenantId = tenantId,
                issueId = issueId,
                authorId = authorId,
                authorApiId = PublicId.new("usr"),
                body = command.body,
                bodyPlainText = command.bodyPlainText,
                transitionId = null,
                statusHistoryId = null,
                editedAt = null,
                createdAt = OffsetDateTime.now(clock),
                updatedAt = OffsetDateTime.now(clock),
              )
          )
        }

      val created =
        service.create(
          CreateWorkItemCommentCommand(
            tenantId = tenantId,
            projectId = projectId,
            workItemApiId = workItemApiId,
            authorId = authorId,
            body = richText("Looks good"),
          )
        )

      created.body shouldBe richText("Looks good")
      created.bodyPlainText shouldBe "Looks good"
    }

    "create rejects when comment permission is missing" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      denyComments(AuthorizationAction("issue.comment.create"))
      coEvery { comments.resolveIssueId(tenantId, projectId, workItemApiId) } returns
        UUID.randomUUID()

      shouldThrow<PermissionDeniedException> {
          service.create(
            CreateWorkItemCommentCommand(
              tenantId = tenantId,
              projectId = projectId,
              workItemApiId = workItemApiId,
              authorId = UUID.randomUUID(),
              body = richText("Denied"),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_COMMENT_CREATE_DENIED
    }

    "create rejects when work item is missing" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val missingWorkItemApiId = PublicId.new("iss").value
      allowComments()
      coEvery { repository.findByApiId(tenantId, projectId, missingWorkItemApiId) } returns null

      shouldThrow<ResourceNotFoundException> {
          service.create(
            CreateWorkItemCommentCommand(
              tenantId = tenantId,
              projectId = projectId,
              workItemApiId = missingWorkItemApiId,
              authorId = UUID.randomUUID(),
              body = richText("Hello"),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND
    }

    "create rejects body that exceeds max length" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      allowComments()
      stubWorkItemAccess(tenantId, projectId, workItemApiId)
      coEvery { comments.resolveIssueId(tenantId, projectId, workItemApiId) } returns
        UUID.randomUUID()

      shouldThrow<InvalidRequestException> {
          service.create(
            CreateWorkItemCommentCommand(
              tenantId = tenantId,
              projectId = projectId,
              workItemApiId = workItemApiId,
              authorId = UUID.randomUUID(),
              body = richText("a".repeat(33_000)),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_COMMENT_BODY_TOO_LONG
    }
  })

private fun sampleIssue(
  tenantId: UUID,
  projectId: UUID,
  apiId: String,
): WorkItemRecord {
  val actorId = UUID.randomUUID()
  return WorkItemRecord(
    id = UUID.randomUUID(),
    apiId = PublicId(apiId),
    tenantId = tenantId,
    projectId = projectId,
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

private fun sampleConfig(tenantId: UUID, apiId: String): IssueTypeConfigDetails {
  val configId = UUID.randomUUID()
  return IssueTypeConfigDetails(
    config =
      IssueTypeConfigRecord(
        id = configId,
        apiId = PublicId(apiId),
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
    statuses = emptyList(),
    properties = emptyList(),
  )
}
