package ink.doa.workbench.core.permission

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class PermissionConditionEvaluatorTest :
  StringSpec({
    val evaluator = PermissionConditionEvaluator()
    val actorId = UUID.randomUUID()
    val reporterId = UUID.randomUUID()
    val assigneeId = UUID.randomUUID()

    fun context(attributes: Map<String, String> = emptyMap()) =
      PermissionConditionContext(actorUserId = actorId, resourceAttributes = attributes)

    fun assigneeIsCurrentUserCondition(): String =
      """
      {"op":"and","args":[
        {"field":"assignee","op":"eq","value":{"var":"user.currentUser"}},
        {"field":"statusGroup","op":"eq","value":"todo"}
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

    "allow condition matches actor and status group" {
      evaluator.evaluate(
        assigneeIsCurrentUserCondition(),
        context(
          mapOf(
            "assignee" to actorId.toString(),
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
            "assignee" to UUID.randomUUID().toString(),
            "statusGroup" to "todo",
          )
        ),
      ) shouldBe PermissionConditionResult.NO_MATCH
    }

    "legacy all syntax matches reporter variable" {
      val condition =
        """
        {"all":[
          {"field":"reporter","op":"eq","value":"issue.reporter"},
          {"field":"statusGroup","op":"eq","value":"todo"}
        ]}
        """
          .trimIndent()

      evaluator.evaluate(
        condition,
        context(
          mapOf(
            "reporter" to reporterId.toString(),
            "statusGroup" to "todo",
          )
        ),
      ) shouldBe PermissionConditionResult.MATCH
    }

    "reporter predicate matches resource attribute" {
      val condition =
        JsonObject(
            mapOf(
              "field" to JsonPrimitive("reporter"),
              "op" to JsonPrimitive("eq"),
              "value" to JsonPrimitive(reporterId.toString()),
            )
          )
          .toString()

      evaluator.evaluate(
        condition,
        context(mapOf("reporter" to reporterId.toString())),
      ) shouldBe PermissionConditionResult.MATCH
    }
  })

class PermissionConditionJsonTest :
  StringSpec({
    "validateAndCanonicalize accepts canonical condition" {
      val raw =
        """
        {"op":"and","args":[
          {"field":"assignee","op":"eq","value":{"var":"user.currentUser"}},
          {"field":"statusGroup","op":"eq","value":"todo"}
        ]}
        """
          .trimIndent()

      PermissionConditionJson.validateAndCanonicalize(raw)?.contains("\"op\":\"and\"") shouldBe true
    }

    "validateAndCanonicalize returns null for blank input" {
      PermissionConditionJson.validateAndCanonicalize(null) shouldBe null
      PermissionConditionJson.validateAndCanonicalize("   ") shouldBe null
    }
  })
