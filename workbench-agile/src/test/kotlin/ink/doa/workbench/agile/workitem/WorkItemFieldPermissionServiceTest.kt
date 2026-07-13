package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.workitem.access.AccessConditionEvaluator
import ink.doa.workbench.agile.workitem.template.FieldParticipation
import ink.doa.workbench.agile.workitem.template.FieldWriteGrant
import ink.doa.workbench.agile.workitem.template.TemplateField
import ink.doa.workbench.agile.workitem.template.TransitionFieldSpec
import ink.doa.workbench.identity.permission.PermissionBindingRepository
import ink.doa.workbench.identity.permission.ResolvedPermissionRule
import ink.doa.workbench.identity.permission.model.AuthorizationAction
import ink.doa.workbench.identity.permission.model.PermissionEffect
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

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
