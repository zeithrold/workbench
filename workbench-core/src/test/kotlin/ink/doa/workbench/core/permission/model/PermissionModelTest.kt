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
  })
