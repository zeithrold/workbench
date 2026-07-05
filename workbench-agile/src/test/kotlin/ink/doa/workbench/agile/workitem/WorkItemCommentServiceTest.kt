package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.PermissionDeniedException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.permission.PermissionBindingRepository
import ink.doa.workbench.core.permission.ResolvedPermissionRule
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.core.workitem.WorkItemCommentRepository
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommentCommand
import ink.doa.workbench.core.workitem.model.DeleteWorkItemCommentCommand
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommentCommand
import ink.doa.workbench.core.workitem.model.WorkItemCommentCreateResult
import ink.doa.workbench.core.workitem.model.WorkItemCommentRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class WorkItemCommentServiceTest :
  StringSpec({
    val clock = Clock.fixed(Instant.parse("2026-07-04T10:15:30Z"), ZoneOffset.UTC)
    val comments = mockk<WorkItemCommentRepository>()
    val bindings = mockk<PermissionBindingRepository>()
    val activityEnqueueSupport = mockk<WorkItemActivityEnqueueSupport>(relaxed = true)
    val service = WorkItemCommentService(comments, bindings, activityEnqueueSupport, clock)

    "create converts plain text body to html and persists comment" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val issueId = UUID.randomUUID()
      val authorId = UUID.randomUUID()
      coEvery { bindings.listActiveRulesForSubject(any(), any(), any(), any()) } returns
        listOf(
          ResolvedPermissionRule(
            bindingId = UUID.randomUUID(),
            action = AuthorizationAction("issue.comment.create"),
            resourcePattern = "issue:*",
            effect = PermissionEffect.ALLOW,
          )
        )
      coEvery { comments.resolveIssueId(tenantId, projectId, "iss_01") } returns issueId
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
                bodyFormat = command.bodyFormat,
                transitionId = null,
                statusHistoryId = null,
                activityId = null,
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
            workItemApiId = "iss_01",
            authorId = authorId,
            body = "Looks good",
          )
        )

      created.body shouldBe "<p>Looks good</p>"
      created.bodyPlainText shouldBe "Looks good"
      created.bodyFormat shouldBe CreateWorkItemCommentCommand.HTML_FORMAT
    }

    "create accepts html comment body and sanitizes scripts" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val issueId = UUID.randomUUID()
      val authorId = UUID.randomUUID()
      coEvery { bindings.listActiveRulesForSubject(any(), any(), any(), any()) } returns
        listOf(
          ResolvedPermissionRule(
            bindingId = UUID.randomUUID(),
            action = AuthorizationAction("issue.comment.create"),
            resourcePattern = "issue:*",
            effect = PermissionEffect.ALLOW,
          )
        )
      coEvery { comments.resolveIssueId(tenantId, projectId, "iss_01") } returns issueId
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
                bodyFormat = command.bodyFormat,
                transitionId = null,
                statusHistoryId = null,
                activityId = null,
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
            workItemApiId = "iss_01",
            authorId = authorId,
            body = "<p>Hello <strong>world</strong></p><script>alert(1)</script>",
          )
        )

      created.body shouldBe "<p>Hello <strong>world</strong></p>"
      created.bodyPlainText shouldBe "Hello world"
      created.body shouldNotContain "script"
    }

    "create rejects blank comment body after sanitization" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      coEvery { bindings.listActiveRulesForSubject(any(), any(), any(), any()) } returns
        listOf(
          ResolvedPermissionRule(
            bindingId = UUID.randomUUID(),
            action = AuthorizationAction("issue.comment.create"),
            resourcePattern = "issue:*",
            effect = PermissionEffect.ALLOW,
          )
        )
      coEvery { comments.resolveIssueId(tenantId, projectId, "iss_01") } returns UUID.randomUUID()

      shouldThrow<InvalidRequestException> {
          service.create(
            CreateWorkItemCommentCommand(
              tenantId = tenantId,
              projectId = projectId,
              workItemApiId = "iss_01",
              authorId = UUID.randomUUID(),
              body = "<script>alert(1)</script>",
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_COMMENT_BODY_REQUIRED
    }

    "update converts body and persists comment" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val issueId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      allowCommentAction(bindings, "issue.comment.update")
      coEvery { comments.resolveIssueId(tenantId, projectId, "iss_01") } returns issueId
      coEvery { comments.update(any(), issueId) } answers
        {
          val command = firstArg<UpdateWorkItemCommentCommand>()
          commentRecord(tenantId, issueId, actorId, clock).copy(body = command.body)
        }

      val updated =
        service.update(
          UpdateWorkItemCommentCommand(
            tenantId = tenantId,
            projectId = projectId,
            workItemApiId = "iss_01",
            commentApiId = "icm_01",
            actorUserId = actorId,
            body = "Updated text",
          )
        )

      updated.body shouldBe "<p>Updated text</p>"
    }

    "delete soft deletes comment" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val issueId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val record = commentRecord(tenantId, issueId, actorId, clock)
      allowCommentAction(bindings, "issue.comment.delete")
      coEvery { comments.resolveIssueId(tenantId, projectId, "iss_01") } returns issueId
      coEvery { comments.softDelete(any(), issueId) } returns record

      service
        .delete(
          DeleteWorkItemCommentCommand(
            tenantId = tenantId,
            projectId = projectId,
            workItemApiId = "iss_01",
            commentApiId = "icm_01",
            actorUserId = actorId,
          )
        )
        .apiId shouldBe record.apiId
    }

    "create rejects when comment permission is missing" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      coEvery { bindings.listActiveRulesForSubject(any(), any(), any(), any()) } returns emptyList()
      coEvery { comments.resolveIssueId(tenantId, projectId, "iss_01") } returns UUID.randomUUID()

      shouldThrow<PermissionDeniedException> {
          service.create(
            CreateWorkItemCommentCommand(
              tenantId = tenantId,
              projectId = projectId,
              workItemApiId = "iss_01",
              authorId = UUID.randomUUID(),
              body = "Denied",
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_COMMENT_CREATE_DENIED
    }

    "update rejects when comment permission is missing" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      coEvery { bindings.listActiveRulesForSubject(any(), any(), any(), any()) } returns emptyList()
      coEvery { comments.resolveIssueId(tenantId, projectId, "iss_01") } returns UUID.randomUUID()

      shouldThrow<PermissionDeniedException> {
          service.update(
            UpdateWorkItemCommentCommand(
              tenantId = tenantId,
              projectId = projectId,
              workItemApiId = "iss_01",
              commentApiId = "icm_01",
              actorUserId = UUID.randomUUID(),
              body = "Denied",
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_COMMENT_UPDATE_DENIED
    }

    "delete rejects when comment permission is missing" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      coEvery { bindings.listActiveRulesForSubject(any(), any(), any(), any()) } returns emptyList()
      coEvery { comments.resolveIssueId(tenantId, projectId, "iss_01") } returns UUID.randomUUID()

      shouldThrow<PermissionDeniedException> {
          service.delete(
            DeleteWorkItemCommentCommand(
              tenantId = tenantId,
              projectId = projectId,
              workItemApiId = "iss_01",
              commentApiId = "icm_01",
              actorUserId = UUID.randomUUID(),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_COMMENT_DELETE_DENIED
    }

    "create rejects when work item is missing" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      allowCommentAction(bindings)
      coEvery { comments.resolveIssueId(tenantId, projectId, "missing") } returns null

      shouldThrow<ResourceNotFoundException> {
          service.create(
            CreateWorkItemCommentCommand(
              tenantId = tenantId,
              projectId = projectId,
              workItemApiId = "missing",
              authorId = UUID.randomUUID(),
              body = "Hello",
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND
    }

    "create rejects body that exceeds max length" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      allowCommentAction(bindings)
      coEvery { comments.resolveIssueId(tenantId, projectId, "iss_01") } returns UUID.randomUUID()

      shouldThrow<InvalidRequestException> {
          service.create(
            CreateWorkItemCommentCommand(
              tenantId = tenantId,
              projectId = projectId,
              workItemApiId = "iss_01",
              authorId = UUID.randomUUID(),
              body = "a".repeat(33_000),
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_COMMENT_BODY_TOO_LONG
    }
  })

private fun allowCommentAction(
  bindings: PermissionBindingRepository,
  action: String = "issue.comment.create",
) {
  coEvery { bindings.listActiveRulesForSubject(any(), any(), any(), any()) } returns
    listOf(
      ResolvedPermissionRule(
        bindingId = UUID.randomUUID(),
        action = AuthorizationAction(action),
        resourcePattern = "issue:*",
        effect = PermissionEffect.ALLOW,
      )
    )
}

private fun commentRecord(
  tenantId: UUID,
  issueId: UUID,
  authorId: UUID,
  clock: Clock,
): WorkItemCommentRecord =
  WorkItemCommentRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("icm"),
    tenantId = tenantId,
    issueId = issueId,
    authorId = authorId,
    authorApiId = PublicId.new("usr"),
    body = "<p>Body</p>",
    bodyPlainText = "Body",
    bodyFormat = CreateWorkItemCommentCommand.HTML_FORMAT,
    transitionId = null,
    statusHistoryId = null,
    activityId = null,
    editedAt = null,
    createdAt = OffsetDateTime.now(clock),
    updatedAt = OffsetDateTime.now(clock),
  )
