package one.ztd.workbench.agile.workitem

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import one.ztd.workbench.agile.workitem.access.CreateWorkItemAccessRuleCommand
import one.ztd.workbench.agile.workitem.access.WorkItemAccessActionType
import one.ztd.workbench.agile.workitem.access.WorkItemAccessRuleRecord
import one.ztd.workbench.agile.workitem.access.WorkItemAccessRuleRepository
import one.ztd.workbench.agile.workitem.access.WorkItemAccessSubjectType
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigDetails
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigRecord
import one.ztd.workbench.agile.workitem.model.WorkItemConfigScope
import one.ztd.workbench.agile.workitem.model.WorkflowTransitionRecord
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.identity.permission.PermissionGroupRepository
import one.ztd.workbench.identity.permission.model.PermissionEffect
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.ids.PublicId

class IssueTypeConfigAccessRuleServiceTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val configApiId = PublicId.new("itc").value
    val workflowId = UUID.randomUUID()
    val transitionId = UUID.randomUUID()
    val transitionApiId = PublicId.new("wtr").value
    val configId = UUID.randomUUID()
    val configs = mockk<IssueTypeConfigRepository>()
    val accessRules = mockk<WorkItemAccessRuleRepository>()
    val workflows = mockk<WorkflowConfigurationRepository>()
    val groups = mockk<PermissionGroupRepository>()
    val users = mockk<UserRepository>()
    val service = IssueTypeConfigAccessRuleService(configs, accessRules, workflows, groups, users)
    val configDetails = sampleConfigDetails(tenantId, configId, configApiId, workflowId)
    val savedRule = sampleRule(tenantId, configId)

    coEvery { configs.findConfig(tenantId, configApiId) } returns configDetails
    coEvery { workflows.listTransitions(tenantId, workflowId) } returns emptyList()

    "list returns rules for config" {
      coEvery { accessRules.listByConfig(tenantId, configId) } returns listOf(savedRule)
      service.list(tenantId, configApiId).single().id shouldBe savedRule.apiId.value
    }

    "create persists comment allow rule" {
      val commandSlot = slot<CreateWorkItemAccessRuleCommand>()
      coEvery { accessRules.create(capture(commandSlot)) } returns savedRule

      val created =
        service.create(
          CreateIssueTypeAccessRuleCommand(
            tenantId = tenantId,
            configApiId = configApiId,
            subjectType = WorkItemAccessSubjectType.ANYONE,
            actionType = WorkItemAccessActionType.COMMENT,
            effect = PermissionEffect.ALLOW,
          )
        )

      created.id shouldBe savedRule.apiId.value
      commandSlot.captured.issueTypeConfigId shouldBe configId
      commandSlot.captured.actionType shouldBe WorkItemAccessActionType.COMMENT
    }

    "create validates transition belongs to workflow" {
      val transition =
        WorkflowTransitionRecord(
          id = transitionId,
          apiId = PublicId(transitionApiId),
          tenantId = tenantId,
          workflowId = workflowId,
          name = "Start",
          fromStatusId = UUID.randomUUID(),
          fromStatusApiId = PublicId.new("sts"),
          toStatusId = UUID.randomUUID(),
          toStatusApiId = PublicId.new("sts"),
          rank = 1,
          preconditionAst = JsonObject(emptyMap()),
          fields = JsonObject(emptyMap()),
          isActive = true,
          createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        )
      coEvery { workflows.findTransition(tenantId, transitionApiId) } returns transition
      coEvery { workflows.listTransitions(tenantId, workflowId) } returns listOf(transition)
      coEvery { accessRules.create(any()) } returns savedRule

      service.create(
        CreateIssueTypeAccessRuleCommand(
          tenantId = tenantId,
          configApiId = configApiId,
          subjectType = WorkItemAccessSubjectType.ANYONE,
          actionType = WorkItemAccessActionType.TRANSITION,
          transitionId = transitionApiId,
          effect = PermissionEffect.ALLOW,
        )
      )

      coVerify { accessRules.create(any()) }
    }

    "create rejects unknown transition" {
      coEvery { workflows.findTransition(tenantId, transitionApiId) } returns null

      shouldThrow<ResourceNotFoundException> {
          service.create(
            CreateIssueTypeAccessRuleCommand(
              tenantId = tenantId,
              configApiId = configApiId,
              subjectType = WorkItemAccessSubjectType.ANYONE,
              actionType = WorkItemAccessActionType.TRANSITION,
              transitionId = transitionApiId,
              effect = PermissionEffect.ALLOW,
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_WORKFLOW_TRANSITION_NOT_FOUND
    }

    "create rejects missing group for in_group subject" {
      shouldThrow<InvalidRequestException> {
          service.create(
            CreateIssueTypeAccessRuleCommand(
              tenantId = tenantId,
              configApiId = configApiId,
              subjectType = WorkItemAccessSubjectType.IN_GROUP,
              actionType = WorkItemAccessActionType.COMMENT,
              effect = PermissionEffect.DENY,
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.REQUEST_VALIDATION_FAILED
    }

    "create rejects missing config" {
      coEvery { configs.findConfig(tenantId, "missing") } returns null

      shouldThrow<ResourceNotFoundException> {
          service.create(
            CreateIssueTypeAccessRuleCommand(
              tenantId = tenantId,
              configApiId = "missing",
              subjectType = WorkItemAccessSubjectType.ANYONE,
              actionType = WorkItemAccessActionType.COMMENT,
              effect = PermissionEffect.ALLOW,
            )
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_CONFIG_NOT_FOUND
    }

    "deactivate removes rule scoped to config" {
      val ruleApiId = savedRule.apiId.value
      coEvery { accessRules.findByApiId(tenantId, ruleApiId) } returns savedRule
      coEvery { accessRules.deactivate(tenantId, savedRule.id) } returns true

      service.deactivate(tenantId, configApiId, ruleApiId) shouldBe true
    }
  })

private fun sampleConfigDetails(
  tenantId: UUID,
  configId: UUID,
  configApiId: String,
  workflowId: UUID,
): IssueTypeConfigDetails {
  val now = OffsetDateTime.parse("2026-01-01T00:00:00Z")
  return IssueTypeConfigDetails(
    config =
      IssueTypeConfigRecord(
        id = configId,
        apiId = PublicId(configApiId),
        tenantId = tenantId,
        scope = WorkItemConfigScope.TENANT,
        projectId = null,
        issueTypeId = UUID.randomUUID(),
        issueTypeApiId = PublicId.new("typ"),
        workflowId = workflowId,
        workflowApiId = PublicId.new("wfl"),
        version = 1,
        nameOverride = null,
        iconOverride = null,
        colorOverride = null,
        rank = 100,
        isActive = true,
        validFrom = now,
        validTo = null,
        createdBy = null,
        createdAt = now,
        updatedAt = now,
        createFields = JsonObject(emptyMap()),
      ),
    statuses = emptyList(),
    properties = emptyList(),
  )
}

private fun sampleRule(tenantId: UUID, configId: UUID): WorkItemAccessRuleRecord {
  val now = OffsetDateTime.parse("2026-01-01T00:00:00Z")
  return WorkItemAccessRuleRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("iar"),
    tenantId = tenantId,
    issueTypeConfigId = configId,
    subjectType = WorkItemAccessSubjectType.ANYONE,
    subjectUserId = null,
    subjectGroupId = null,
    subjectRoleCode = null,
    actionType = WorkItemAccessActionType.COMMENT,
    transitionId = null,
    fieldKey = null,
    effect = PermissionEffect.ALLOW,
    condition = JsonObject(emptyMap()),
    rank = 100,
    isActive = true,
    createdAt = now,
    updatedAt = now,
  )
}
