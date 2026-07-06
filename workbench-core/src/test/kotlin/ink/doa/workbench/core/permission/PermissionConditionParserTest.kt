package ink.doa.workbench.core.permission

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.query.ConditionNode
import ink.doa.workbench.core.workitem.query.QueryField
import ink.doa.workbench.core.workitem.query.QueryOperator
import ink.doa.workbench.core.workitem.query.QueryValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class PermissionConditionParserTest :
  StringSpec({
    fun assigneeIsCurrentUserCondition(): String =
      """
      {"op":"and","args":[
        {"field":"assignee","op":"eq","value":{"var":"user.currentUser"}},
        {"field":"statusGroup","op":"eq","value":"todo"}
      ]}
      """
        .trimIndent()

    "parseOrNull returns null for empty object" {
      PermissionConditionParser.parseOrNull(JsonObject(emptyMap())).shouldBeNull()
    }

    "parseOrNull returns null for invalid predicate" {
      PermissionConditionParser.parseOrNull(
          JsonObject(mapOf("field" to JsonPrimitive("statusGroup")))
        )
        .shouldBeNull()
    }

    "parseOrNull parses canonical predicate" {
      PermissionConditionParser.parseOrNull(
        JsonObject(
          mapOf(
            "field" to JsonPrimitive("statusGroup"),
            "op" to JsonPrimitive("eq"),
            "value" to JsonPrimitive("todo"),
          )
        )
      ) shouldBe
        ConditionNode.Predicate(
          field = QueryField.System("statusGroup"),
          op = QueryOperator.EQ,
          value = QueryValue.Literal(JsonPrimitive("todo")),
        )
    }

    "canonicalizeOrThrow returns canonical json" {
      PermissionConditionParser.canonicalizeOrThrow(assigneeIsCurrentUserCondition())
        .contains("\"op\":\"and\"") shouldBe true
    }

    "canonicalizeOrThrow rejects malformed json" {
      shouldThrow<InvalidRequestException> { PermissionConditionParser.canonicalizeOrThrow("{bad") }
        .errorCode shouldBe WorkbenchErrorCode.PERMISSION_POLICY_RULE_CONDITION_INVALID
    }

    "canonicalizeOrThrow rejects empty object" {
      shouldThrow<InvalidRequestException> { PermissionConditionParser.canonicalizeOrThrow("{}") }
        .errorCode shouldBe WorkbenchErrorCode.PERMISSION_POLICY_RULE_CONDITION_INVALID
    }

    "canonicalizeOrThrow rejects predicate missing operator" {
      shouldThrow<InvalidRequestException> {
          PermissionConditionParser.canonicalizeOrThrow("""{"field":"statusGroup"}""")
        }
        .errorCode shouldBe WorkbenchErrorCode.PERMISSION_POLICY_RULE_CONDITION_INVALID
    }

    "canonicalizeOrThrow rejects unknown object shape" {
      shouldThrow<InvalidRequestException> {
          PermissionConditionParser.canonicalizeOrThrow("""{"unknown":"value"}""")
        }
        .errorCode shouldBe WorkbenchErrorCode.PERMISSION_POLICY_RULE_CONDITION_INVALID
    }
  })
