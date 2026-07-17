package one.ztd.workbench.agile.workitem

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.agile.workitem.model.WorkItemRecord
import one.ztd.workbench.agile.workitem.model.WorkItemStatusGroup
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.ids.PublicId

private const val ACTOR_API_ID = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0"
private const val PROJECT_API_ID = "prj_01JABCDEFGHJKMNPQRSTVWXYZ1"

class WorkItemConditionEvaluatorTest :
  StringSpec({
    val actorId = UUID.randomUUID()
    val issue = workItem(actorId)
    val evaluator = WorkItemConditionEvaluator()

    fun context(
      item: WorkItemRecord = issue,
      properties: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
      childIssuesNotDone: Long = 0,
    ) = WorkItemConditionContext(item, ACTOR_API_ID, PROJECT_API_ID, properties, childIssuesNotDone)

    "evaluates canonical and composition with current user variables" {
      val ast =
        JsonObject(
          mapOf(
            "op" to JsonPrimitive("and"),
            "args" to
              JsonArray(
                listOf(
                  JsonObject(
                    mapOf(
                      "field" to JsonPrimitive("issue.assignee"),
                      "op" to JsonPrimitive("eq"),
                      "value" to JsonObject(mapOf("var" to JsonPrimitive("user.currentUser"))),
                    )
                  ),
                  predicate("issue.statusGroup", "eq", "todo"),
                )
              ),
          )
        )

      evaluator.evaluate(ast, context()) shouldBe true
    }

    "rejects legacy all syntax" {
      val ast =
        JsonObject(
          mapOf(
            "all" to
              JsonArray(
                listOf(
                  predicate("issue.assignee", "eq", "user.currentUser"),
                  predicate("issue.statusGroup", "eq", "todo"),
                )
              )
          )
        )

      shouldThrow<InvalidRequestException> { evaluator.evaluate(ast, context()) }
    }

    "evaluates child aggregate preconditions" {
      val ast = predicate("children.notDone", "eq", JsonPrimitive(0))

      evaluator.evaluate(ast, context(childIssuesNotDone = 1)) shouldBe false
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
                      "field" to JsonPrimitive("issue.assignee"),
                      "op" to JsonPrimitive("eq"),
                      "value" to JsonObject(mapOf("var" to JsonPrimitive("user.currentUser"))),
                    )
                  ),
                  JsonObject(
                    mapOf(
                      "field" to JsonPrimitive("issue.statusGroup"),
                      "op" to JsonPrimitive("eq"),
                      "value" to JsonPrimitive("todo"),
                    )
                  ),
                )
              ),
          )
        )

      evaluator.evaluate(ast, context()) shouldBe true
    }

    "returns true for empty or unparseable condition" {
      evaluator.evaluate(JsonObject(emptyMap()), context()) shouldBe true
    }

    "evaluates or and not combinators" {
      val orAst =
        JsonObject(
          mapOf(
            "op" to JsonPrimitive("or"),
            "args" to
              JsonArray(
                listOf(
                  predicate("issue.statusGroup", "eq", "done"),
                  predicate("issue.statusGroup", "eq", "todo"),
                )
              ),
          )
        )
      val notAst =
        JsonObject(
          mapOf(
            "op" to JsonPrimitive("not"),
            "arg" to predicate("issue.statusGroup", "eq", "done"),
          )
        )

      evaluator.evaluate(orAst, context()) shouldBe true
      evaluator.evaluate(notAst, context()) shouldBe true
      evaluator.evaluate(
        JsonObject(
          mapOf(
            "op" to JsonPrimitive("not"),
            "arg" to predicate("issue.statusGroup", "eq", "todo"),
          )
        ),
        context(),
      ) shouldBe false
    }

    "evaluates comparison operators on child aggregate" {
      val ctx = context(childIssuesNotDone = 3)

      evaluator.evaluate(predicate("children.notDone", "gt", JsonPrimitive(2)), ctx) shouldBe true
      evaluator.evaluate(predicate("children.notDone", "gte", JsonPrimitive(3)), ctx) shouldBe true
      evaluator.evaluate(predicate("children.notDone", "lt", JsonPrimitive(4)), ctx) shouldBe true
      evaluator.evaluate(predicate("children.notDone", "lte", JsonPrimitive(3)), ctx) shouldBe true
    }

    "evaluates membership operators on status" {
      val statusApiId = issue.statusApiId.value
      val ctx = context()

      evaluator.evaluate(predicate("issue.status", "in", statusApiId), ctx) shouldBe true
      evaluator.evaluate(
        predicate("issue.status", "not_in", JsonArray(listOf(JsonPrimitive("sts_other")))),
        ctx,
      ) shouldBe true
    }

    "evaluates empty and not empty on assignee" {
      val assigned = context()
      val unassigned = context(workItem(actorId, assigneeId = null))

      evaluator.evaluate(predicate("issue.assignee", "is_not_empty", JsonNull), assigned) shouldBe
        true
      evaluator.evaluate(predicate("issue.assignee", "is_empty", JsonNull), unassigned) shouldBe
        true
    }

    "evaluates string and array operators on custom properties" {
      val ctx =
        context(
          properties =
            mapOf(
              "note" to JsonPrimitive("hello world"),
              "tags" to JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))),
            )
        )

      evaluator.evaluate(predicate("property.note", "contains", "world"), ctx) shouldBe true
      evaluator.evaluate(predicate("property.note", "not_contains", "missing"), ctx) shouldBe true
      evaluator.evaluate(
        predicate("property.tags", "has_any", JsonArray(listOf(JsonPrimitive("b")))),
        ctx,
      ) shouldBe true
      evaluator.evaluate(
        predicate(
          "property.tags",
          "has_all",
          JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))),
        ),
        ctx,
      ) shouldBe true
      evaluator.evaluate(
        predicate("property.tags", "has_none", JsonArray(listOf(JsonPrimitive("z")))),
        ctx,
      ) shouldBe true
      evaluator.evaluate(
        predicate("property.structured", "eq", """{"k":"v"}"""),
        context(properties = mapOf("structured" to JsonObject(mapOf("k" to JsonPrimitive("v"))))),
      ) shouldBe true
    }

    "resolves property field by apiId and code" {
      val propertyApiId = PublicId.new("fld").value
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
        context(issueWithProperty),
      ) shouldBe true
      evaluator.evaluate(
        predicate("property.$propertyApiId", "eq", "low"),
        context(issueWithProperty),
      ) shouldBe true
      evaluator.evaluate(
        predicate("property.customNote", "eq", "from-context"),
        context(properties = mapOf("customNote" to JsonPrimitive("from-context"))),
      ) shouldBe true
    }

    "resolves issue variables with null assignee" {
      val unassignedIssue = workItem(actorId, assigneeId = null)
      val ctx = context(unassignedIssue)

      evaluator.evaluate(
        JsonObject(
          mapOf(
            "field" to JsonPrimitive("issue.assignee"),
            "op" to JsonPrimitive("eq"),
            "value" to JsonNull,
          )
        ),
        ctx,
      ) shouldBe true
      evaluator.evaluate(
        JsonObject(
          mapOf(
            "field" to JsonPrimitive("issue.reporter"),
            "op" to JsonPrimitive("eq"),
            "value" to JsonObject(mapOf("var" to JsonPrimitive("issue.reporter"))),
          )
        ),
        ctx,
      ) shouldBe true
    }

    "rejects unsupported operator" {
      shouldThrow<InvalidRequestException> {
        evaluator.evaluate(
          predicate(
            "issue.statusGroup",
            "between",
            JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2))),
          ),
          context(),
        )
      }
    }

    "rejects unsupported variable and non-numeric comparison" {
      shouldThrow<InvalidRequestException> {
        evaluator.evaluate(
          JsonObject(
            mapOf(
              "field" to JsonPrimitive("issue.assignee"),
              "op" to JsonPrimitive("eq"),
              "value" to JsonObject(mapOf("var" to JsonPrimitive("issue.unknown"))),
            )
          ),
          context(),
        )
      }

      shouldThrow<InvalidRequestException> {
        evaluator.evaluate(predicate("issue.statusGroup", "gt", "todo"), context())
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
    reporterApiId = PublicId(ACTOR_API_ID),
    assigneeApiId = assigneeId?.let { PublicId(ACTOR_API_ID) },
    sprintApiId = null,
    properties = properties,
    createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
  )
