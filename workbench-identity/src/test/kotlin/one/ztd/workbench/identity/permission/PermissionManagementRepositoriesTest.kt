package one.ztd.workbench.identity.permission

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.identity.permission.model.AuthorizationAction
import one.ztd.workbench.identity.permission.model.PermissionEffect
import one.ztd.workbench.kernel.common.ids.PublicId

class PermissionManagementRepositoriesTest :
  StringSpec({
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val tenantId = UUID.randomUUID()

    "permission principal types map to database values" {
      PermissionPrincipalType.USER.dbValue shouldBe "user"
      PermissionPrincipalType.GROUP.dbValue shouldBe "group"
      GroupMemberStatus.ACTIVE.dbValue shouldBe "active"
    }

    "permission group record stores tenant scoped metadata" {
      val record =
        PermissionGroupRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("pgr"),
          tenantId = tenantId,
          code = "developers",
          name = "Developers",
          description = null,
          builtin = false,
          createdAt = now,
          updatedAt = now,
        )

      record.code shouldBe "developers"
      record.tenantId shouldBe tenantId
    }

    "permission policy rule record stores action pattern" {
      val record =
        PermissionPolicyRuleRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("ppr"),
          policyId = UUID.randomUUID(),
          action = AuthorizationAction("project.read"),
          resourcePattern = "project:*",
          effect = PermissionEffect.ALLOW,
          conditionJson = null,
          createdAt = now,
        )

      record.resourcePattern shouldBe "project:*"
    }

    "resolved permission rule carries binding metadata" {
      val rule =
        ResolvedPermissionRule(
          bindingId = UUID.randomUUID(),
          action = AuthorizationAction("project.read"),
          resourcePattern = "project:*",
          effect = PermissionEffect.ALLOW,
        )

      rule.resourcePattern shouldBe "project:*"
      rule.effect shouldBe PermissionEffect.ALLOW
    }

    "policy document commands carry ordered replacement rules" {
      val policyId = UUID.randomUUID()
      val createRule =
        CreatePermissionPolicyRuleCommand(
          policyId = policyId,
          action = AuthorizationAction("issue.update"),
          resourcePattern = "issue:*",
          position = 1,
        )
      val create =
        CreatePermissionPolicyCommand(
          tenantId = tenantId,
          code = "editor",
          name = "Editor",
          description = null,
          rules = listOf(createRule),
        )
      val replacementRule =
        ReplacePermissionPolicyRuleCommand(
          apiId = "prl_existing",
          action = createRule.action,
          resourcePattern = createRule.resourcePattern,
          effect = PermissionEffect.DENY,
          conditionJson = null,
          position = 0,
        )
      val replacement =
        ReplacePermissionPolicyCommand(
          policyId = policyId,
          expectedUpdatedAt = now,
          name = "Restricted editor",
          description = null,
          rules = listOf(replacementRule),
        )

      create.rules.single().position shouldBe 1
      replacement.rules.single().effect shouldBe PermissionEffect.DENY
    }
  })
