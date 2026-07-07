package ink.doa.workbench.core.permission

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PermissionConditionJsonTest :
  StringSpec({
    fun assigneeIsCurrentUserCondition(): String =
      """
      {"op":"and","args":[
        {"field":"issue.assignee","op":"eq","value":{"var":"user.currentUser"}},
        {"field":"issue.statusGroup","op":"eq","value":"todo"}
      ]}
      """
        .trimIndent()

    "validateAndCanonicalize accepts canonical condition" {
      PermissionConditionJson.validateAndCanonicalize(assigneeIsCurrentUserCondition())
        ?.contains("\"op\":\"and\"") shouldBe true
    }

    "validateAndCanonicalize returns null for blank input" {
      PermissionConditionJson.validateAndCanonicalize(null) shouldBe null
      PermissionConditionJson.validateAndCanonicalize("   ") shouldBe null
    }

    "validateAndCanonicalize rejects malformed json" {
      shouldThrow<InvalidRequestException> {
          PermissionConditionJson.validateAndCanonicalize("{not-json")
        }
        .errorCode shouldBe WorkbenchErrorCode.PERMISSION_POLICY_RULE_CONDITION_INVALID
    }

    "validateAndCanonicalize rejects non-object root" {
      shouldThrow<InvalidRequestException> {
          PermissionConditionJson.validateAndCanonicalize("[]")
        }
        .errorCode shouldBe WorkbenchErrorCode.PERMISSION_POLICY_RULE_CONDITION_INVALID
    }

    "validateAndCanonicalize rejects empty object" {
      shouldThrow<InvalidRequestException> {
          PermissionConditionJson.validateAndCanonicalize("{}")
        }
        .errorCode shouldBe WorkbenchErrorCode.PERMISSION_POLICY_RULE_CONDITION_INVALID
    }

    "validateAndCanonicalize rejects predicate missing operator" {
      shouldThrow<InvalidRequestException> {
          PermissionConditionJson.validateAndCanonicalize("""{"field":"statusGroup"}""")
        }
        .errorCode shouldBe WorkbenchErrorCode.PERMISSION_POLICY_RULE_CONDITION_INVALID
    }
  })
