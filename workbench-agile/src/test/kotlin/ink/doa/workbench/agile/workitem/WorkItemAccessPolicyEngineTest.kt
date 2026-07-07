package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.core.workitem.access.AccessConditionEvaluator
import ink.doa.workbench.core.workitem.access.WorkItemAccessActionType
import ink.doa.workbench.core.workitem.access.WorkItemAccessActor
import ink.doa.workbench.core.workitem.access.WorkItemAccessEvaluationContext
import ink.doa.workbench.core.workitem.access.WorkItemAccessRuleRecord
import ink.doa.workbench.core.workitem.access.WorkItemAccessRuleRepository
import ink.doa.workbench.core.workitem.access.WorkItemAccessSubjectType
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.core.workitem.template.TemplateField
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class WorkItemAccessPolicyEngineTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val configId = UUID.randomUUID()
    val transitionId = UUID.randomUUID()
    val actorId = UUID.randomUUID()
    val groupId = UUID.randomUUID()
    val accessRules = mockk<WorkItemAccessRuleRepository>()
    val principalResolver = mockk<WorkItemAccessPrincipalResolver>(relaxed = true)
    val bindingEvaluator = mockk<WorkItemBindingPermissionEvaluator>(relaxed = true)
    val engine =
      WorkItemAccessPolicyEngine(
        accessRules,
        principalResolver,
        bindingEvaluator,
        AccessConditionEvaluator(),
      )

    fun evaluation(
      actor: WorkItemAccessActor = WorkItemAccessActor(actorId, emptySet(), emptySet()),
      assigneeId: UUID? = actorId,
    ): WorkItemAccessEvaluationContext {
      val workItem = sampleWorkItem(tenantId, actorId, assigneeId)
      return WorkItemAccessEvaluationContext(
        actor = actor,
        workItem = workItem,
        issueTypeConfigId = configId,
        properties = emptyMap(),
      )
    }

    fun stubRules(rules: List<WorkItemAccessRuleRecord>) {
      coEvery { accessRules.listByConfig(tenantId, configId) } returns rules
    }

    "allows transition when no rules exist" {
      stubRules(emptyList())
      engine.isTransitionPermitted(configId, transitionId, evaluation()) shouldBe true
    }

    "denies transition when deny rule matches actor" {
      stubRules(
        listOf(
          accessRule(
            AccessRuleFixture(
              subjectType = WorkItemAccessSubjectType.USER,
              subjectUserId = actorId,
              actionType = WorkItemAccessActionType.TRANSITION,
              transitionId = transitionId,
              effect = PermissionEffect.DENY,
            )
          )
        )
      )
      engine.isTransitionPermitted(configId, transitionId, evaluation()) shouldBe false
    }

    "allows transition when allow rule matches group member" {
      stubRules(
        listOf(
          accessRule(
            AccessRuleFixture(
              subjectType = WorkItemAccessSubjectType.IN_GROUP,
              subjectGroupId = groupId,
              actionType = WorkItemAccessActionType.TRANSITION,
              transitionId = transitionId,
              effect = PermissionEffect.ALLOW,
            )
          )
        )
      )
      val actor = WorkItemAccessActor(actorId, setOf(groupId), emptySet())
      engine.isTransitionPermitted(configId, transitionId, evaluation(actor)) shouldBe true
    }

    "deny wins over allow for the same transition" {
      stubRules(
        listOf(
          accessRule(
            AccessRuleFixture(
              subjectType = WorkItemAccessSubjectType.ANYONE,
              actionType = WorkItemAccessActionType.TRANSITION,
              transitionId = transitionId,
              effect = PermissionEffect.ALLOW,
            )
          ),
          accessRule(
            AccessRuleFixture(
              subjectType = WorkItemAccessSubjectType.USER,
              subjectUserId = actorId,
              actionType = WorkItemAccessActionType.TRANSITION,
              transitionId = transitionId,
              effect = PermissionEffect.DENY,
            )
          ),
        )
      )
      engine.isTransitionPermitted(configId, transitionId, evaluation()) shouldBe false
    }

    "ignores rules for other transitions" {
      stubRules(
        listOf(
          accessRule(
            AccessRuleFixture(
              subjectType = WorkItemAccessSubjectType.ANYONE,
              actionType = WorkItemAccessActionType.TRANSITION,
              transitionId = UUID.randomUUID(),
              effect = PermissionEffect.DENY,
            )
          )
        )
      )
      engine.isTransitionPermitted(configId, transitionId, evaluation()) shouldBe true
    }

    "field write all deny blocks specific field writes" {
      stubRules(
        listOf(
          accessRule(
            AccessRuleFixture(
              subjectType = WorkItemAccessSubjectType.ANYONE,
              actionType = WorkItemAccessActionType.FIELD_WRITE_ALL,
              effect = PermissionEffect.DENY,
            )
          )
        )
      )
      val field = TemplateField.Property(apiId = null, code = "resolution")
      engine.isFieldWritePermitted(configId, field, evaluation()) shouldBe false
    }

    "field write wildcard rule matches property field" {
      stubRules(
        listOf(
          accessRule(
            AccessRuleFixture(
              subjectType = WorkItemAccessSubjectType.ANYONE,
              actionType = WorkItemAccessActionType.FIELD_WRITE,
              fieldKey = "*",
              effect = PermissionEffect.ALLOW,
            )
          )
        )
      )
      val field = TemplateField.Property(apiId = "fld_01JABCDEFGHJKMNPQRSTVWXYZ0", code = "points")
      engine.isFieldWritePermitted(configId, field, evaluation()) shouldBe true
    }

    "comment permission defaults to allow without rules" {
      stubRules(emptyList())
      engine.isCommentPermitted(configId, evaluation()) shouldBe true
    }

    "comment deny rule blocks comment action" {
      stubRules(
        listOf(
          accessRule(
            AccessRuleFixture(
              subjectType = WorkItemAccessSubjectType.IN_ROLE,
              subjectRoleCode = "viewer",
              actionType = WorkItemAccessActionType.COMMENT,
              effect = PermissionEffect.DENY,
            )
          )
        )
      )
      val actor = WorkItemAccessActor(actorId, emptySet(), setOf("viewer"))
      engine.isCommentPermitted(configId, evaluation(actor)) shouldBe false
    }

    "bindingAllowsComment delegates to binding evaluator" {
      val projectId = UUID.randomUUID()
      coEvery {
        bindingEvaluator.allowsComment(tenantId, projectId, actorId, COMMENT_ACTION)
      } returns true
      kotlinx.coroutines.runBlocking {
        engine.bindingAllowsComment(tenantId, projectId, actorId, COMMENT_ACTION) shouldBe true
      }
    }
  })

private val COMMENT_ACTION = AuthorizationAction("issue.comment.create")

private data class AccessRuleFixture(
  val subjectType: WorkItemAccessSubjectType,
  val actionType: WorkItemAccessActionType,
  val effect: PermissionEffect,
  val subjectUserId: UUID? = null,
  val subjectGroupId: UUID? = null,
  val subjectRoleCode: String? = null,
  val transitionId: UUID? = null,
  val fieldKey: String? = null,
  val condition: JsonObject = JsonObject(emptyMap()),
)

private fun accessRule(fixture: AccessRuleFixture): WorkItemAccessRuleRecord {
  val now = OffsetDateTime.parse("2026-01-01T00:00:00Z")
  return WorkItemAccessRuleRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("iar"),
    tenantId = UUID.randomUUID(),
    issueTypeConfigId = UUID.randomUUID(),
    subjectType = fixture.subjectType,
    subjectUserId = fixture.subjectUserId,
    subjectGroupId = fixture.subjectGroupId,
    subjectRoleCode = fixture.subjectRoleCode,
    actionType = fixture.actionType,
    transitionId = fixture.transitionId,
    fieldKey = fixture.fieldKey,
    effect = fixture.effect,
    condition = fixture.condition,
    rank = 100,
    isActive = true,
    createdAt = now,
    updatedAt = now,
  )
}

private fun sampleWorkItem(
  tenantId: UUID,
  reporterId: UUID,
  assigneeId: UUID?,
): WorkItemRecord =
  WorkItemRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("iss"),
    tenantId = tenantId,
    projectId = UUID.randomUUID(),
    issueTypeApiId = PublicId.new("typ"),
    issueTypeConfigApiId = PublicId.new("itc"),
    key = "CORE-1",
    title = "Issue",
    description = null,
    statusId = UUID.randomUUID(),
    statusApiId = PublicId.new("sts"),
    statusGroup = WorkItemStatusGroup.TODO,
    reporterId = reporterId,
    assigneeId = assigneeId,
    priorityApiId = null,
    reporterApiId = PublicId.new("usr"),
    assigneeApiId = assigneeId?.let { PublicId.new("usr") },
    sprintApiId = null,
    properties = JsonObject(emptyMap()),
    createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
  )
