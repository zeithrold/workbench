package one.ztd.workbench.agile.workitem

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.agile.workitem.access.AccessConditionContext
import one.ztd.workbench.agile.workitem.access.AccessConditionEvaluator
import one.ztd.workbench.agile.workitem.template.FieldParticipation
import one.ztd.workbench.agile.workitem.template.FieldWriteGrant
import one.ztd.workbench.agile.workitem.template.TemplateField
import one.ztd.workbench.agile.workitem.template.TransitionFieldSpec
import one.ztd.workbench.identity.permission.PermissionBindingRepository
import one.ztd.workbench.identity.permission.ResolvedPermissionRule
import one.ztd.workbench.identity.permission.model.AuthorizationAction
import one.ztd.workbench.identity.permission.model.PermissionEffect

class WorkItemFieldPermissionServiceTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val userId = UUID.randomUUID()
    val clock = Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC)
    val bindings = mockk<PermissionBindingRepository>()
    val bindingEvaluator =
      WorkItemBindingPermissionEvaluator(bindings, AccessConditionEvaluator(), clock)
    val accessPolicy = mockk<WorkItemAccessPolicyEngine>()
    val service = WorkItemFieldPermissionService(accessPolicy)
    val field = TemplateField.Property(apiId = null, code = "resolution")

    coEvery { accessPolicy.bindingAllowsFieldWrite(any(), any(), any()) } coAnswers
      {
        bindingEvaluator.allowsFieldWrite(
          firstArg(),
          secondArg(),
          thirdArg(),
        )
      }
    coEvery { accessPolicy.isFieldWritePermitted(any(), any(), any()) } returns true

    fun context(operation: FieldPermissionOperation) =
      WorkItemFieldPermissionContext(
        tenantId,
        projectId,
        userId,
        "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
        operation,
      )

    fun issueUpdateAllow() =
      ResolvedPermissionRule(
        bindingId = UUID.randomUUID(),
        action = AuthorizationAction("issue.update"),
        resourcePattern = "issue:*",
        effect = PermissionEffect.ALLOW,
      )

    fun fieldWriteRule(resource: String, effect: PermissionEffect) =
      ResolvedPermissionRule(
        bindingId = UUID.randomUUID(),
        action = AuthorizationAction("issue.field.write"),
        resourcePattern = resource,
        effect = effect,
      )

    "inherits issue.update when no field rule matches" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, projectId, any()) } returns
        listOf(issueUpdateAllow())

      service.bindingAllowsWrite(context(FieldPermissionOperation.UPDATE), field) shouldBe true
    }

    "explicit field deny overrides issue.update" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, projectId, any()) } returns
        listOf(
          issueUpdateAllow(),
          fieldWriteRule("issue:field:property.resolution", PermissionEffect.DENY),
        )

      service.bindingAllowsWrite(context(FieldPermissionOperation.UPDATE), field) shouldBe false
    }

    "create allows field write when no rules match" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, projectId, any()) } returns
        emptyList()

      service.bindingAllowsWrite(context(FieldPermissionOperation.CREATE), field) shouldBe true
    }

    "update denies field write when no rules match" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, projectId, any()) } returns
        emptyList()

      service.bindingAllowsWrite(context(FieldPermissionOperation.UPDATE), field) shouldBe false
    }

    "field allow rule grants write without issue.update" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, projectId, any()) } returns
        listOf(fieldWriteRule("issue:field:property.resolution", PermissionEffect.ALLOW))

      service.bindingAllowsWrite(context(FieldPermissionOperation.UPDATE), field) shouldBe true
    }

    "wildcard field allow grants write for any property field" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, projectId, any()) } returns
        listOf(fieldWriteRule("issue:field:*", PermissionEffect.ALLOW))

      service.bindingAllowsWrite(context(FieldPermissionOperation.UPDATE), field) shouldBe true
    }

    "wildcard field deny blocks write even with issue.update" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, projectId, any()) } returns
        listOf(issueUpdateAllow(), fieldWriteRule("issue:field:*", PermissionEffect.DENY))

      service.bindingAllowsWrite(context(FieldPermissionOperation.UPDATE), field) shouldBe false
    }

    "preloaded binding rules are reused across fields without repository queries" {
      clearMocks(bindings, answers = false, recordedCalls = true)
      val rules = listOf(issueUpdateAllow())
      val preloaded = context(FieldPermissionOperation.UPDATE).copy(bindingRules = rules)

      service.bindingAllowsWrite(preloaded, field) shouldBe true
      service.bindingAllowsWrite(preloaded, TemplateField.System("assignee")) shouldBe true

      coVerify(exactly = 0) {
        bindings.listActiveRulesForSubject(any(), any(), any(), any())
      }
    }

    "evaluates issue actions and comment grants from active bindings" {
      val action = AuthorizationAction("issue.transition")
      val allow =
        ResolvedPermissionRule(
          bindingId = UUID.randomUUID(),
          action = action,
          resourcePattern = "issue:*",
          effect = PermissionEffect.ALLOW,
        )
      val deny = allow.copy(bindingId = UUID.randomUUID(), effect = PermissionEffect.DENY)
      val conditionContext =
        AccessConditionContext.fromResourceAttributes(
          "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
          emptyMap<String, String>(),
        )
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, projectId, any()) } returns
        listOf(allow)

      bindingEvaluator.allowsComment(tenantId, projectId, userId, action) shouldBe true
      bindingEvaluator.allowsIssueAction(listOf(allow), action, conditionContext) shouldBe true
      bindingEvaluator.allowsIssueAction(listOf(allow, deny), action, conditionContext) shouldBe
        false
    }

    "resolvePolicy returns read-only submission for immutable and system-only grants" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, projectId, any()) } returns
        listOf(issueUpdateAllow())

      service.resolvePolicy(
        context(FieldPermissionOperation.UPDATE),
        field,
        TransitionFieldSpec(
          participation = FieldParticipation.OPTIONAL,
          writeGrant = FieldWriteGrant.IMMUTABLE,
        ),
      ) shouldBe
        FieldMutationPolicy(
          submission = FieldSubmissionPolicy.READ_ONLY,
          bindingAllowsWrite = true,
        )

      service.resolvePolicy(
        context(FieldPermissionOperation.UPDATE),
        field,
        TransitionFieldSpec(
          participation = FieldParticipation.OPTIONAL,
          writeGrant = FieldWriteGrant.SYSTEM_ONLY,
        ),
      ) shouldBe
        FieldMutationPolicy(
          submission = FieldSubmissionPolicy.READ_ONLY,
          bindingAllowsWrite = true,
        )
    }

    "resolvePolicy honors transition writable without binding lookup" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, projectId, any()) } returns
        emptyList()

      service.resolvePolicy(
        context(FieldPermissionOperation.UPDATE),
        field,
        TransitionFieldSpec(
          participation = FieldParticipation.OPTIONAL,
          writeGrant = FieldWriteGrant.TRANSITION_WRITABLE,
        ),
      ) shouldBe
        FieldMutationPolicy(
          submission = FieldSubmissionPolicy.TRANSITION_OVERRIDE,
          bindingAllowsWrite = false,
        )
    }

    "resolvePolicy inherit delegates binding to bindingAllowsWrite" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, projectId, any()) } returns
        listOf(issueUpdateAllow())

      service.resolvePolicy(
        context(FieldPermissionOperation.UPDATE),
        field,
        TransitionFieldSpec(
          participation = FieldParticipation.OPTIONAL,
          writeGrant = FieldWriteGrant.INHERIT,
        ),
      ) shouldBe
        FieldMutationPolicy(
          submission = FieldSubmissionPolicy.INHERIT_BINDING,
          bindingAllowsWrite = true,
        )
    }

    "resolvePolicy rejects automatic participation" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, projectId, any()) } returns
        listOf(issueUpdateAllow())

      service.resolvePolicy(
        context(FieldPermissionOperation.UPDATE),
        field,
        TransitionFieldSpec(
          participation = FieldParticipation.AUTOMATIC,
          writeGrant = FieldWriteGrant.INHERIT,
        ),
      ) shouldBe
        FieldMutationPolicy(
          submission = FieldSubmissionPolicy.READ_ONLY,
          bindingAllowsWrite = true,
        )
    }

    "resolvePatchPolicy mirrors bindingAllowsWrite" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, projectId, any()) } returns
        listOf(issueUpdateAllow())

      service.resolvePatchPolicy(context(FieldPermissionOperation.UPDATE), field) shouldBe
        FieldMutationPolicy(
          submission = FieldSubmissionPolicy.INHERIT_BINDING,
          bindingAllowsWrite = true,
        )
    }
  })
