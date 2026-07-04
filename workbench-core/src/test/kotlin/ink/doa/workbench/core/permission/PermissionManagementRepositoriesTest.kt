package ink.doa.workbench.core.permission

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.PermissionEffect
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

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
  })
