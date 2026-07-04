package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.permission.PermissionBindingRepository
import ink.doa.workbench.core.permission.ResolvedPermissionRule
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.core.workitem.template.TemplateField
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

    "inherits issue.update when no field rule matches" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, projectId, any()) } returns
        listOf(
          ResolvedPermissionRule(
            bindingId = UUID.randomUUID(),
            action = AuthorizationAction("issue.update"),
            resourcePattern = "issue:*",
            effect = PermissionEffect.ALLOW,
          )
        )

      service.canWriteField(
        WorkItemFieldPermissionContext(
          tenantId,
          projectId,
          userId,
          FieldPermissionOperation.UPDATE,
        ),
        field,
      ) shouldBe true
    }

    "explicit field deny overrides issue.update" {
      coEvery { bindings.listActiveRulesForSubject(userId, tenantId, projectId, any()) } returns
        listOf(
          ResolvedPermissionRule(
            bindingId = UUID.randomUUID(),
            action = AuthorizationAction("issue.update"),
            resourcePattern = "issue:*",
            effect = PermissionEffect.ALLOW,
          ),
          ResolvedPermissionRule(
            bindingId = UUID.randomUUID(),
            action = AuthorizationAction("issue.field.write"),
            resourcePattern = "issue:field:property.resolution",
            effect = PermissionEffect.DENY,
          ),
        )

      service.canWriteField(
        WorkItemFieldPermissionContext(
          tenantId,
          projectId,
          userId,
          FieldPermissionOperation.UPDATE,
        ),
        field,
      ) shouldBe false
    }
  })
