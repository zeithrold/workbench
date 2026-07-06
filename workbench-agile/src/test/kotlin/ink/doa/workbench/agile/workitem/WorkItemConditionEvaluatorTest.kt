package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class WorkItemConditionEvaluatorTest :
  StringSpec({
    val actorId = UUID.randomUUID()
    val issue = workItem(actorId)
    val evaluator = WorkItemConditionEvaluator()

    "evaluates nested all and current user variables" {
      val ast =
        JsonObject(
          mapOf(
            "all" to
              JsonArray(
                listOf(
                  predicate("assignee", "eq", "user.currentUser"),
                  predicate("statusGroup", "eq", "todo"),
                )
              )
          )
        )

      evaluator.evaluate(
        ast,
        WorkItemConditionContext(issue, actorId, properties = emptyMap()),
      ) shouldBe true
    }

    "evaluates child aggregate preconditions" {
      val ast = predicate("children.notDone", "eq", JsonPrimitive(0))

      evaluator.evaluate(
        ast,
        WorkItemConditionContext(issue, actorId, properties = emptyMap(), childIssuesNotDone = 1),
      ) shouldBe false
    }

    "evaluates query style condition ast and variables" {
      val ast =
        JsonObject(
          mapOf(
            "op" to JsonPrimitive("and"),
            "args" to
              JsonArray(
                listOf(
                  JsonObject(
                    mapOf(
                      "field" to JsonPrimitive("assignee"),
                      "op" to JsonPrimitive("eq"),
                      "value" to JsonObject(mapOf("var" to JsonPrimitive("user.currentUser"))),
                    )
                  ),
                  JsonObject(
                    mapOf(
                      "field" to JsonPrimitive("statusGroup"),
                      "op" to JsonPrimitive("eq"),
                      "value" to JsonPrimitive("todo"),
                    )
                  ),
                )
              ),
          )
        )

      evaluator.evaluate(
        ast,
        WorkItemConditionContext(issue, actorId, properties = emptyMap()),
      ) shouldBe true
    }

    "returns true for empty or unparseable condition" {
      evaluator.evaluate(
        JsonObject(emptyMap()),
        WorkItemConditionContext(issue, actorId, properties = emptyMap()),
      ) shouldBe true
    }

    "evaluates or and not combinators" {
      val orAst =
        JsonObject(
          mapOf(
            "any" to
              JsonArray(
                listOf(
                  predicate("statusGroup", "eq", "done"),
                  predicate("statusGroup", "eq", "todo"),
                )
              )
          )
        )
      val notAst = JsonObject(mapOf("not" to predicate("statusGroup", "eq", "done")))

      evaluator.evaluate(
        orAst,
        WorkItemConditionContext(issue, actorId, properties = emptyMap()),
      ) shouldBe true
      evaluator.evaluate(
        notAst,
        WorkItemConditionContext(issue, actorId, properties = emptyMap()),
      ) shouldBe true
      evaluator.evaluate(
        JsonObject(mapOf("not" to predicate("statusGroup", "eq", "todo"))),
        WorkItemConditionContext(issue, actorId, properties = emptyMap()),
      ) shouldBe false
    }

    "evaluates comparison operators on child aggregate" {
      val context =
        WorkItemConditionContext(issue, actorId, properties = emptyMap(), childIssuesNotDone = 3)

      evaluator.evaluate(predicate("children.notDone", "gt", JsonPrimitive(2)), context) shouldBe
        true
      evaluator.evaluate(predicate("children.notDone", "gte", JsonPrimitive(3)), context) shouldBe
        true
      evaluator.evaluate(predicate("children.notDone", "lt", JsonPrimitive(4)), context) shouldBe
        true
      evaluator.evaluate(predicate("children.notDone", "lte", JsonPrimitive(3)), context) shouldBe
        true
    }

    "evaluates membership operators on status" {
      val statusApiId = issue.statusApiId.value
      val context = WorkItemConditionContext(issue, actorId, properties = emptyMap())

      evaluator.evaluate(
        predicate("status", "in", statusApiId),
        context,
      ) shouldBe true
      evaluator.evaluate(
        predicate("status", "not_in", JsonArray(listOf(JsonPrimitive("sts_other")))),
        context,
      ) shouldBe true
    }

    "evaluates empty and not empty on assignee" {
      val assigned = WorkItemConditionContext(issue, actorId, properties = emptyMap())
      val unassigned =
        WorkItemConditionContext(
          workItem(actorId, assigneeId = null),
          actorId,
          properties = emptyMap(),
        )

      evaluator.evaluate(predicate("assignee", "is_not_empty", JsonNull), assigned) shouldBe true
      evaluator.evaluate(predicate("assignee", "is_empty", JsonNull), unassigned) shouldBe true
    }

    "evaluates string and array operators on custom properties" {
      val context =
        WorkItemConditionContext(
          issue,
          actorId,
          properties =
            mapOf(
              "note" to JsonPrimitive("hello world"),
              "tags" to JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))),
            ),
        )

      evaluator.evaluate(predicate("note", "contains", "world"), context) shouldBe true
      evaluator.evaluate(predicate("note", "not_contains", "missing"), context) shouldBe true
      evaluator.evaluate(
        predicate("tags", "has_any", JsonArray(listOf(JsonPrimitive("b")))),
        context,
      ) shouldBe true
      evaluator.evaluate(
        predicate("tags", "has_all", JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b")))),
        context,
      ) shouldBe true
      evaluator.evaluate(
        predicate("tags", "has_none", JsonArray(listOf(JsonPrimitive("z")))),
        context,
      ) shouldBe true
      evaluator.evaluate(
        predicate("structured", "eq", """{"k":"v"}"""),
        WorkItemConditionContext(
          issue,
          actorId,
          properties = mapOf("structured" to JsonObject(mapOf("k" to JsonPrimitive("v")))),
        ),
      ) shouldBe true
    }

    "resolves property field by apiId and code" {
      val propertyApiId = "fld_severity"
      val issueWithProperty =
        workItem(
          actorId,
          properties =
            JsonObject(
              mapOf("severity" to JsonPrimitive("high"), propertyApiId to JsonPrimitive("low"))
            ),
        )

      evaluator.evaluate(
        predicate("property.severity", "eq", "high"),
        WorkItemConditionContext(issueWithProperty, actorId, properties = emptyMap()),
      ) shouldBe true
      evaluator.evaluate(
        predicate("property.$propertyApiId", "eq", "low"),
        WorkItemConditionContext(issueWithProperty, actorId, properties = emptyMap()),
      ) shouldBe true
      evaluator.evaluate(
        predicate("customNote", "eq", "from-context"),
        WorkItemConditionContext(
          issue,
          actorId,
          properties = mapOf("customNote" to JsonPrimitive("from-context")),
        ),
      ) shouldBe true
    }

    "resolves issue variables with null assignee" {
      val unassignedIssue = workItem(actorId, assigneeId = null)
      val context = WorkItemConditionContext(unassignedIssue, actorId, properties = emptyMap())

      evaluator.evaluate(
        JsonObject(
          mapOf(
            "field" to JsonPrimitive("issue.assignee"),
            "op" to JsonPrimitive("eq"),
            "value" to JsonNull,
          )
        ),
        context,
      ) shouldBe true
      evaluator.evaluate(
        JsonObject(
          mapOf(
            "field" to JsonPrimitive("issue.reporter"),
            "op" to JsonPrimitive("eq"),
            "value" to JsonObject(mapOf("var" to JsonPrimitive("issue.reporter"))),
          )
        ),
        context,
      ) shouldBe true
    }

    "rejects unsupported operator" {
      shouldThrow<InvalidRequestException> {
        evaluator.evaluate(
          predicate(
            "statusGroup",
            "between",
            JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2))),
          ),
          WorkItemConditionContext(issue, actorId, properties = emptyMap()),
        )
      }
    }

    "rejects unsupported variable and non-numeric comparison" {
      shouldThrow<InvalidRequestException> {
        evaluator.evaluate(
          JsonObject(
            mapOf(
              "field" to JsonPrimitive("assignee"),
              "op" to JsonPrimitive("eq"),
              "value" to JsonObject(mapOf("var" to JsonPrimitive("issue.unknown"))),
            )
          ),
          WorkItemConditionContext(issue, actorId, properties = emptyMap()),
        )
      }

      shouldThrow<InvalidRequestException> {
        evaluator.evaluate(
          predicate("statusGroup", "gt", "todo"),
          WorkItemConditionContext(issue, actorId, properties = emptyMap()),
        )
      }
    }
  })

private fun predicate(
  field: String,
  op: String,
  value: String,
): JsonObject = predicate(field, op, JsonPrimitive(value))

private fun predicate(
  field: String,
  op: String,
  value: kotlinx.serialization.json.JsonElement,
): JsonObject =
  JsonObject(
    buildMap {
      put("field", JsonPrimitive(field))
      put("op", JsonPrimitive(op))
      if (op !in setOf("is_empty", "is_not_empty", "missing", "exists")) {
        put("value", value)
      }
    }
  )

private fun workItem(
  actorId: UUID,
  assigneeId: UUID? = actorId,
  properties: JsonObject = JsonObject(emptyMap()),
): WorkItemRecord =
  WorkItemRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("iss"),
    tenantId = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    issueTypeApiId = PublicId.new("typ"),
    issueTypeConfigApiId = PublicId.new("itc"),
    key = "CORE-1",
    title = "Issue",
    description = null,
    statusId = UUID.randomUUID(),
    statusApiId = PublicId.new("sts"),
    statusGroup = WorkItemStatusGroup.TODO,
    reporterId = actorId,
    assigneeId = assigneeId,
    priorityApiId = null,
    reporterApiId = PublicId.new("usr"),
    assigneeApiId = assigneeId?.let { PublicId.new("usr") },
    sprintApiId = null,
    properties = properties,
    createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
  )
