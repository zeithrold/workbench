package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID
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
              kotlinx.serialization.json.JsonArray(
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
  JsonObject(mapOf("field" to JsonPrimitive(field), "op" to JsonPrimitive(op), "value" to value))

private fun workItem(actorId: UUID): WorkItemRecord =
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
    assigneeId = actorId,
    priorityApiId = null,
    reporterApiId = PublicId.new("usr"),
    assigneeApiId = PublicId.new("usr"),
    sprintApiId = null,
    properties = JsonObject(emptyMap()),
    createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
  )
