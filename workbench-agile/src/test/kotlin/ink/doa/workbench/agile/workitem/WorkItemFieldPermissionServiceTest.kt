package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.permission.PermissionBindingRepository
import ink.doa.workbench.core.permission.ResolvedPermissionRule
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.core.workitem.template.FieldParticipation
import ink.doa.workbench.core.workitem.template.FieldWriteGrant
import ink.doa.workbench.core.workitem.template.TemplateField
import ink.doa.workbench.core.workitem.template.TransitionFieldSpec
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
    val service = WorkItemFieldPermissionService(bindings, clock)
    val field = TemplateField.Property(apiId = null, code = "resolution")

    fun context(operation: FieldPermissionOperation) =
      WorkItemFieldPermissionContext(tenantId, projectId, userId, operation)

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

    "resolvePolicy returns non-submittable for immutable and system-only grants" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, projectId, any()) } returns
        listOf(issueUpdateAllow())

      service.resolvePolicy(
        context(FieldPermissionOperation.UPDATE),
        field,
        TransitionFieldSpec(
          participation = FieldParticipation.OPTIONAL,
          writeGrant = FieldWriteGrant.IMMUTABLE,
        ),
      ) shouldBe FieldMutationPolicy(allowsUserSubmission = false, bindingAllowsWrite = true)

      service.resolvePolicy(
        context(FieldPermissionOperation.UPDATE),
        field,
        TransitionFieldSpec(
          participation = FieldParticipation.OPTIONAL,
          writeGrant = FieldWriteGrant.SYSTEM_ONLY,
        ),
      ) shouldBe FieldMutationPolicy(allowsUserSubmission = false, bindingAllowsWrite = true)
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
      ) shouldBe FieldMutationPolicy(allowsUserSubmission = true, bindingAllowsWrite = false)
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
      ) shouldBe FieldMutationPolicy(allowsUserSubmission = true, bindingAllowsWrite = true)
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
      ) shouldBe FieldMutationPolicy(allowsUserSubmission = false, bindingAllowsWrite = true)
    }

    "resolvePatchPolicy mirrors bindingAllowsWrite" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, projectId, any()) } returns
        listOf(issueUpdateAllow())

      service.resolvePatchPolicy(context(FieldPermissionOperation.UPDATE), field) shouldBe
        FieldMutationPolicy(allowsUserSubmission = true, bindingAllowsWrite = true)
    }
  })
