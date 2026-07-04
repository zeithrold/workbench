package ink.doa.workbench.core.permission.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PermissionModelTest :
  StringSpec({
    "permission actions accept stable dotted capability names" {
      PermissionAction("issue.transition").code shouldBe "issue.transition"
    }

    "permission actions reject ambiguous strings" {
      shouldThrow<IllegalArgumentException> { PermissionAction("issue-transition") }
    }

    "permission rule stores actions and resource pattern" {
      val rule =
        PermissionRule(
          effect = PermissionEffect.ALLOW,
          actions = setOf(PermissionAction("issue.read")),
          resource = ResourcePattern("project:*"),
          condition = PermissionCondition.FieldEquals("status", "open"),
        )

      rule.actions.single().code shouldBe "issue.read"
      (rule.condition as PermissionCondition.FieldEquals).field shouldBe "status"
    }

    "authorization decisions expose allow and deny reasons" {
      val allow = AuthorizationDecision.Allow(DecisionReason("grant_allowed", "allowed"))
      val deny = AuthorizationDecision.Deny(DecisionReason("grant_missing", "denied"))
      (allow is AuthorizationDecision.Allow) shouldBe true
      (deny is AuthorizationDecision.Deny) shouldBe true
      allow.reason.code shouldBe "grant_allowed"
    }
  })
