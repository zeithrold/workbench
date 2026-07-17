package one.ztd.workbench.application.permission

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.identity.permission.PermissionConditionContext
import one.ztd.workbench.identity.permission.PermissionConditionResult
import one.ztd.workbench.kernel.common.errors.InvalidRequestException

class PermissionConditionEvaluatorTest :
  StringSpec({
    val evaluator = PermissionConditionEvaluator()
    val actorApiId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0"
    val reporterApiId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1"
    fun context(attributes: Map<String, String> = emptyMap()) =
      PermissionConditionContext(actorUserApiId = actorApiId, resourceAttributes = attributes)

    fun assigneeIsCurrentUserCondition(): String =
      """
      {"op":"and","args":[
        {"field":"issue.assignee","op":"eq","value":{"var":"user.currentUser"}},
        {"field":"issue.statusGroup","op":"eq","value":"todo"}
      ]}
      """
        .trimIndent()

    "blank condition matches" {
      evaluator.evaluate(null, context()) shouldBe PermissionConditionResult.MATCH
      evaluator.evaluate("  ", context()) shouldBe PermissionConditionResult.MATCH
    }

    "invalid condition json is invalid" {
      evaluator.evaluate("{not-json", context()) shouldBe PermissionConditionResult.INVALID
    }

    "empty object condition is invalid" {
      evaluator.evaluate("{}", context()) shouldBe PermissionConditionResult.INVALID
      evaluator.evaluate("{ }", context()) shouldBe PermissionConditionResult.INVALID
    }

    "allow condition matches actor and status group" {
      evaluator.evaluate(
        assigneeIsCurrentUserCondition(),
        context(
          mapOf(
            "assignee" to actorApiId,
            "statusGroup" to "todo",
          )
        ),
      ) shouldBe PermissionConditionResult.MATCH
    }

    "allow condition fails when assignee differs" {
      evaluator.evaluate(
        assigneeIsCurrentUserCondition(),
        context(
          mapOf(
            "assignee" to reporterApiId,
            "statusGroup" to "todo",
          )
        ),
      ) shouldBe PermissionConditionResult.NO_MATCH
    }

    "legacy all syntax is invalid" {
      val condition =
        """
        {"all":[
          {"field":"issue.reporter","op":"eq","value":{"var":"issue.reporter"}},
          {"field":"issue.statusGroup","op":"eq","value":"todo"}
        ]}
        """
          .trimIndent()

      shouldThrow<InvalidRequestException> {
        evaluator.evaluate(
          condition,
          context(
            mapOf(
              "reporter" to reporterApiId,
              "statusGroup" to "todo",
            )
          ),
        )
      }
    }

    "rejects uuid literal in stored condition" {
      val condition =
        """
        {"field":"issue.assignee","op":"eq","value":"550e8400-e29b-41d4-a716-446655440000"}
        """
          .trimIndent()

      shouldThrow<InvalidRequestException> { evaluator.evaluate(condition, context()) }
    }
  })
