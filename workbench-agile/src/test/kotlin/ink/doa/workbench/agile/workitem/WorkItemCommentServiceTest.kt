package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.permission.PermissionBindingRepository
import ink.doa.workbench.core.permission.ResolvedPermissionRule
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.core.workitem.WorkItemCommentRepository
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommentCommand
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
    val workItems = mockk<WorkItemRepository>()
    val bindings = mockk<PermissionBindingRepository>()
    val service = WorkItemCommentService(comments, workItems, bindings, clock)

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
            editedAt = null,
            createdAt = OffsetDateTime.now(clock),
            updatedAt = OffsetDateTime.now(clock),
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
            editedAt = null,
            createdAt = OffsetDateTime.now(clock),
            updatedAt = OffsetDateTime.now(clock),
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
  })
