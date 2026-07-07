package ink.doa.workbench.core.workitem.access

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.permission.PermissionConditionResult
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
import kotlinx.serialization.json.buildJsonObject

class AccessConditionEvaluatorTest :
  StringSpec({
    val evaluator = AccessConditionEvaluator()
    val actorId = UUID.randomUUID()
    val workItem = sampleWorkItem(actorId)

    fun context(
      item: WorkItemRecord? = workItem,
      properties: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
      childIssuesNotDone: Long = 0,
      attributes: Map<String, String> = emptyMap(),
    ): AccessConditionContext =
      AccessConditionContext(
        actorUserId = actorId,
        workItem = item,
        properties = properties,
        childIssuesNotDone = childIssuesNotDone,
        resourceAttributes = attributes,
      )

    "evaluateObject returns true for empty condition" {
      evaluator.evaluateObject(JsonObject(emptyMap()), context()) shouldBe true
    }

    "evaluateJsonString matches assignee from work item" {
      val condition =
        """
        {"op":"eq","field":"assignee","value":{"var":"user.currentUser"}}
        """
          .trimIndent()

      evaluator.evaluateJsonString(condition, context()) shouldBe PermissionConditionResult.MATCH
    }

    "evaluateJsonString fails when assignee differs" {
      val condition =
        """
        {"op":"eq","field":"assignee","value":{"var":"user.currentUser"}}
        """
          .trimIndent()

      evaluator.evaluateJsonString(condition, context(sampleWorkItem(UUID.randomUUID()))) shouldBe
        PermissionConditionResult.NO_MATCH
    }

    "evaluateJsonString treats blank condition as match" {
      evaluator.evaluateJsonString("  ", context()) shouldBe PermissionConditionResult.MATCH
      evaluator.evaluateJsonString("{not-json", context()) shouldBe
        PermissionConditionResult.INVALID
    }

    "evaluateObject supports and or and not composition" {
      val andCondition = buildJsonObject {
        put("op", JsonPrimitive("and"))
        put(
          "args",
          JsonArray(
            listOf(
              predicate("statusGroup", "eq", JsonPrimitive("todo")),
              predicate("actor", "eq", JsonPrimitive(actorId.toString())),
            )
          ),
        )
      }
      val orCondition = buildJsonObject {
        put("op", JsonPrimitive("or"))
        put(
          "args",
          JsonArray(
            listOf(
              predicate("statusGroup", "eq", JsonPrimitive("done")),
              predicate("statusGroup", "eq", JsonPrimitive("todo")),
            )
          ),
        )
      }
      val notCondition = buildJsonObject {
        put("op", JsonPrimitive("not"))
        put("arg", predicate("statusGroup", "eq", JsonPrimitive("done")))
      }

      evaluator.evaluateObject(andCondition, context()) shouldBe true
      evaluator.evaluateObject(orCondition, context()) shouldBe true
      evaluator.evaluateObject(notCondition, context()) shouldBe true
    }

    "evaluates comparison operators on child aggregate" {
      val ctx = context(childIssuesNotDone = 3)
      evaluator.evaluateObject(predicate("children.notDone", "gt", JsonPrimitive(2)), ctx) shouldBe
        true
      evaluator.evaluateObject(predicate("children.notDone", "gte", JsonPrimitive(3)), ctx) shouldBe
        true
      evaluator.evaluateObject(predicate("children.notDone", "lt", JsonPrimitive(4)), ctx) shouldBe
        true
      evaluator.evaluateObject(predicate("children.notDone", "lte", JsonPrimitive(3)), ctx) shouldBe
        true
    }

    "evaluates membership operators" {
      val statusApiId = workItem.statusApiId.value
      val ctx = context()
      evaluator.evaluateObject(predicate("status", "in", JsonPrimitive(statusApiId)), ctx) shouldBe
        true
      evaluator.evaluateObject(predicate("status", "neq", JsonPrimitive("done")), ctx) shouldBe true
      evaluator.evaluateObject(
        predicate("status", "not_in", JsonArray(listOf(JsonPrimitive("sts_other")))),
        ctx,
      ) shouldBe true
    }

    "evaluates empty checks using resource attributes" {
      val withAssignee =
        context(
          item = null,
          attributes = mapOf("assignee" to actorId.toString(), "statusGroup" to "todo"),
        )
      val withoutAssignee = context(item = null, attributes = mapOf("statusGroup" to "todo"))

      evaluator.evaluateObject(predicate("assignee", "is_not_empty"), withAssignee) shouldBe true
      evaluator.evaluateObject(predicate("assignee", "is_empty"), withoutAssignee) shouldBe true
    }

    "evaluates issue prefixed system fields" {
      val ctx = context()
      evaluator.evaluateObject(
        predicate("issue.statusGroup", "eq", JsonPrimitive("todo")),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("issue.reporter", "eq", JsonPrimitive(actorId.toString())),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("issue.assignee", "eq", JsonPrimitive(actorId.toString())),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("issue.project", "eq", JsonPrimitive(workItem.projectId.toString())),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("issue.issueType", "eq", JsonPrimitive(workItem.issueTypeApiId.value)),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate(
          "issue.issueTypeConfig",
          "eq",
          JsonPrimitive(workItem.issueTypeConfigApiId.value),
        ),
        ctx,
      ) shouldBe true
    }

    "evaluates property fields from context map" {
      val propertyKey = "fld_points"
      val ctx = context(properties = mapOf(propertyKey to JsonPrimitive("5")))
      evaluator.evaluateObject(
        buildJsonObject {
          put("field", JsonPrimitive("property.$propertyKey"))
          put("op", JsonPrimitive("eq"))
          put("value", JsonPrimitive("5"))
        },
        ctx,
      ) shouldBe true
    }

    "evaluates status group from work item record" {
      val ctx = context()
      evaluator.evaluateObject(predicate("statusGroup", "eq", JsonPrimitive("todo")), ctx) shouldBe
        true
      evaluator.evaluateObject(
        predicate("issue.status", "eq", JsonPrimitive(workItem.statusApiId.value)),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("issueTypeConfig", "eq", JsonPrimitive(workItem.issueTypeConfigApiId.value)),
        ctx,
      ) shouldBe true
    }

    "reads custom values from work item properties map" {
      val item = workItem.copy(properties = JsonObject(mapOf("custom" to JsonPrimitive("value"))))
      val ctx = context(item = item)
      evaluator.evaluateObject(predicate("custom", "eq", JsonPrimitive("value")), ctx) shouldBe true
    }

    "issue reporter falls back to null without work item or attribute" {
      val ctx = context(item = null)
      evaluator.evaluateObject(predicate("issue.reporter", "is_empty"), ctx) shouldBe true
    }

    "actor aliases resolve to current user id" {
      val ctx = context()
      evaluator.evaluateObject(
        predicate("actorId", "eq", JsonPrimitive(actorId.toString())),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("user.currentUser", "eq", JsonPrimitive(actorId.toString())),
        ctx,
      ) shouldBe true
    }

    "evaluateJsonString returns invalid for empty object condition" {
      evaluator.evaluateJsonString("{}", context()) shouldBe PermissionConditionResult.INVALID
    }

    "issue assignee is empty when work item has no assignee" {
      val unassigned = workItem.copy(assigneeId = null, assigneeApiId = null)
      val ctx = context(item = unassigned)
      evaluator.evaluateObject(predicate("issue.assignee", "is_empty"), ctx) shouldBe true
    }

    "numeric operators reject non numeric values" {
      val ctx = context(properties = mapOf("points" to JsonPrimitive("not-a-number")))
      shouldThrow<InvalidRequestException> {
        evaluator.evaluateObject(
          buildJsonObject {
            put("field", JsonPrimitive("property.points"))
            put("op", JsonPrimitive("gt"))
            put("value", JsonPrimitive(1))
          },
          ctx,
        )
      }
    }

    "rejects unsupported operators" {
      shouldThrow<InvalidRequestException> {
        evaluator.evaluateObject(
          buildJsonObject {
            put("field", JsonPrimitive("statusGroup"))
            put("op", JsonPrimitive("unknown"))
            put("value", JsonPrimitive("todo"))
          },
          context(),
        )
      }
    }

    "evaluates array membership operators on properties" {
      val tags = JsonArray(listOf(JsonPrimitive("bug"), JsonPrimitive("urgent")))
      val ctx = context(properties = mapOf("tags" to tags))
      evaluator.evaluateObject(
        predicate("property.tags", "has_any", JsonArray(listOf(JsonPrimitive("urgent")))),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate(
          "property.tags",
          "has_all",
          JsonArray(listOf(JsonPrimitive("bug"), JsonPrimitive("urgent"))),
        ),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("property.tags", "has_none", JsonArray(listOf(JsonPrimitive("feature")))),
        ctx,
      ) shouldBe true
    }

    "evaluates contains operators on scalar properties" {
      val ctx = context(properties = mapOf("summary" to JsonPrimitive("Fix login bug")))
      evaluator.evaluateObject(
        predicate("property.summary", "contains", JsonPrimitive("login")),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("property.summary", "not_contains", JsonPrimitive("deploy")),
        ctx,
      ) shouldBe true
    }

    "resolves legacy field aliases and resource attributes" {
      val ctx =
        context(
          item = null,
          attributes =
            mapOf(
              "reporter" to actorId.toString(),
              "assignee" to actorId.toString(),
              "status" to workItem.statusApiId.value,
              "statusGroup" to "todo",
              "issueType" to workItem.issueTypeApiId.value,
              "issueTypeConfig" to workItem.issueTypeConfigApiId.value,
              "project" to workItem.projectId.toString(),
            ),
        )
      evaluator.evaluateObject(
        predicate("reporterId", "eq", JsonPrimitive(actorId.toString())),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("assigneeId", "eq", JsonPrimitive(actorId.toString())),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("statusId", "eq", JsonPrimitive(workItem.statusApiId.value)),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("issueTypeId", "eq", JsonPrimitive(workItem.issueTypeApiId.value)),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("issueTypeConfigId", "eq", JsonPrimitive(workItem.issueTypeConfigApiId.value)),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("projectId", "eq", JsonPrimitive(workItem.projectId.toString())),
        ctx,
      ) shouldBe true
    }

    "evaluates work item backed aliases" {
      val ctx = context()
      evaluator.evaluateObject(
        predicate("reporter", "eq", JsonPrimitive(actorId.toString())),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("reporterId", "eq", JsonPrimitive(actorId.toString())),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("assignee", "eq", JsonPrimitive(actorId.toString())),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("assigneeId", "eq", JsonPrimitive(actorId.toString())),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("status", "eq", JsonPrimitive(workItem.statusApiId.value)),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("statusId", "eq", JsonPrimitive(workItem.statusApiId.value)),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("issueType", "eq", JsonPrimitive(workItem.issueTypeApiId.value)),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("issueTypeId", "eq", JsonPrimitive(workItem.issueTypeApiId.value)),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("issueTypeConfig", "eq", JsonPrimitive(workItem.issueTypeConfigApiId.value)),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("project", "eq", JsonPrimitive(workItem.projectId.toString())),
        ctx,
      ) shouldBe true
      evaluator.evaluateObject(
        predicate("projectId", "eq", JsonPrimitive(workItem.projectId.toString())),
        ctx,
      ) shouldBe true
    }

    "rejects unsupported condition variables" {
      shouldThrow<InvalidRequestException> {
        evaluator.evaluateObject(
          buildJsonObject {
            put("field", JsonPrimitive("statusGroup"))
            put("op", JsonPrimitive("eq"))
            put("value", JsonObject(mapOf("var" to JsonPrimitive("unknown.variable"))))
          },
          context(),
        )
      }
    }

    "AccessConditionContext fromEvaluation copies actor and work item state" {
      val actor = WorkItemAccessActor(actorId, setOf(UUID.randomUUID()), setOf("admin"))
      val evaluation =
        WorkItemAccessEvaluationContext(
          actor = actor,
          workItem = workItem,
          issueTypeConfigId = UUID.randomUUID(),
          properties = mapOf("priority" to JsonPrimitive("high")),
          childIssuesNotDone = 2,
        )
      val derived = AccessConditionContext.fromEvaluation(evaluation)
      derived.actorUserId shouldBe actorId
      derived.actorGroupIds shouldBe actor.groupIds
      derived.workItem shouldBe workItem
      derived.childIssuesNotDone shouldBe 2
    }

    "AccessConditionContext fromResourceAttributes stores actor attributes" {
      val derived =
        AccessConditionContext.fromResourceAttributes(
          actorUserId = actorId,
          resourceAttributes = mapOf("assignee" to actorId.toString()),
        )
      derived.actorUserId shouldBe actorId
      derived.resourceAttributes["assignee"] shouldBe actorId.toString()
    }
  })

class WorkItemAccessRuleRecordsTest :
  StringSpec({
    "subject and action enums round trip db values" {
      WorkItemAccessSubjectType.fromDbValue("in_group") shouldBe WorkItemAccessSubjectType.IN_GROUP
      WorkItemAccessSubjectType.fromDbValue("not_in_group") shouldBe
        WorkItemAccessSubjectType.NOT_IN_GROUP
      WorkItemAccessSubjectType.fromDbValue("in_role") shouldBe WorkItemAccessSubjectType.IN_ROLE
      WorkItemAccessSubjectType.fromDbValue("not_in_role") shouldBe
        WorkItemAccessSubjectType.NOT_IN_ROLE
      WorkItemAccessSubjectType.fromDbValue("user") shouldBe WorkItemAccessSubjectType.USER
      WorkItemAccessSubjectType.fromDbValue("anyone") shouldBe WorkItemAccessSubjectType.ANYONE
      WorkItemAccessActionType.fromDbValue("field_write_all") shouldBe
        WorkItemAccessActionType.FIELD_WRITE_ALL
      WorkItemAccessActionType.fromDbValue("field_write") shouldBe
        WorkItemAccessActionType.FIELD_WRITE
      WorkItemAccessActionType.fromDbValue("transition") shouldBe
        WorkItemAccessActionType.TRANSITION
      WorkItemAccessActionType.fromDbValue("comment") shouldBe WorkItemAccessActionType.COMMENT
    }

    "access actor and evaluation context hold principal state" {
      val userId = UUID.randomUUID()
      val groupId = UUID.randomUUID()
      val configId = UUID.randomUUID()
      val actor = WorkItemAccessActor(userId, setOf(groupId), setOf("member"))
      actor.userId shouldBe userId
      actor.groupIds shouldBe setOf(groupId)

      val command =
        CreateWorkItemAccessRuleCommand(
          tenantId = UUID.randomUUID(),
          issueTypeConfigId = configId,
          subjectType = WorkItemAccessSubjectType.ANYONE,
          actionType = WorkItemAccessActionType.COMMENT,
          effect = ink.doa.workbench.core.permission.model.PermissionEffect.ALLOW,
        )
      command.actionType shouldBe WorkItemAccessActionType.COMMENT

      val now = OffsetDateTime.parse("2026-01-01T00:00:00Z")
      val record =
        WorkItemAccessRuleRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("war"),
          tenantId = command.tenantId,
          issueTypeConfigId = configId,
          subjectType = WorkItemAccessSubjectType.NOT_IN_ROLE,
          subjectUserId = null,
          subjectGroupId = null,
          subjectRoleCode = "viewer",
          actionType = WorkItemAccessActionType.FIELD_WRITE,
          transitionId = null,
          fieldKey = "priority",
          effect = ink.doa.workbench.core.permission.model.PermissionEffect.DENY,
          condition = JsonObject(emptyMap()),
          rank = 10,
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )
      record.fieldKey shouldBe "priority"
      record.subjectType shouldBe WorkItemAccessSubjectType.NOT_IN_ROLE
    }
  })

private fun predicate(
  field: String,
  op: String,
  value: kotlinx.serialization.json.JsonElement = JsonNull,
): JsonObject = buildJsonObject {
  put("field", JsonPrimitive(field))
  put("op", JsonPrimitive(op))
  if (op !in setOf("is_empty", "is_not_empty")) {
    put("value", value)
  }
}

private fun sampleWorkItem(assigneeId: UUID): WorkItemRecord {
  val issueTypeId = UUID.randomUUID()
  return WorkItemRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("iss"),
    tenantId = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    issueTypeId = issueTypeId,
    issueTypeApiId = PublicId.new("typ"),
    issueTypeConfigApiId = PublicId.new("itc"),
    key = "CORE-1",
    title = "Issue",
    description = null,
    statusId = UUID.randomUUID(),
    statusApiId = PublicId.new("sts"),
    statusGroup = WorkItemStatusGroup.TODO,
    reporterId = assigneeId,
    assigneeId = assigneeId,
    priorityApiId = null,
    reporterApiId = PublicId.new("usr"),
    assigneeApiId = PublicId.new("usr"),
    sprintApiId = null,
    properties = JsonObject(emptyMap()),
    createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
  )
}
