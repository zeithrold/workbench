package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.permission.PermissionGroupRepository
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkflowConfigurationRepository
import ink.doa.workbench.core.workitem.access.CreateWorkItemAccessRuleCommand
import ink.doa.workbench.core.workitem.access.WorkItemAccessActionType
import ink.doa.workbench.core.workitem.access.WorkItemAccessRuleRecord
import ink.doa.workbench.core.workitem.access.WorkItemAccessRuleRepository
import ink.doa.workbench.core.workitem.access.WorkItemAccessSubjectType
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
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

class IssueTypeConfigAccessRuleServiceTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val configApiId = PublicId.new("itc").value
    val workflowId = UUID.randomUUID()
    val transitionId = UUID.randomUUID()
    val configId = UUID.randomUUID()
    val configs = mockk<IssueTypeConfigRepository>()
    val accessRules = mockk<WorkItemAccessRuleRepository>()
    val workflows = mockk<WorkflowConfigurationRepository>()
    val groups = mockk<PermissionGroupRepository>()
    val service = IssueTypeConfigAccessRuleService(configs, accessRules, workflows, groups)
    val configDetails = sampleConfigDetails(tenantId, configId, configApiId, workflowId)
    val savedRule = sampleRule(tenantId, configId)

    coEvery { configs.findConfig(tenantId, configApiId) } returns configDetails

    "list returns rules for config" {
      coEvery { accessRules.listByConfig(tenantId, configId) } returns listOf(savedRule)
      service.list(tenantId, configApiId) shouldBe listOf(savedRule)
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

      created shouldBe savedRule
      commandSlot.captured.issueTypeConfigId shouldBe configId
      commandSlot.captured.actionType shouldBe WorkItemAccessActionType.COMMENT
    }

    "create validates transition belongs to workflow" {
      coEvery { workflows.listTransitions(tenantId, workflowId) } returns
        listOf(
          WorkflowTransitionRecord(
            id = transitionId,
            apiId = PublicId.new("wtr"),
            tenantId = tenantId,
            workflowId = workflowId,
            name = "Start",
            fromStatusId = UUID.randomUUID(),
            fromStatusApiId = PublicId.new("sts"),
            toStatusId = UUID.randomUUID(),
            toStatusApiId = PublicId.new("sts"),
            rank = 1,
            preconditionAst = JsonObject(emptyMap()),
            permissionCondition = JsonObject(emptyMap()),
            fields = JsonObject(emptyMap()),
            isActive = true,
            createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
          )
        )
      coEvery { accessRules.create(any()) } returns savedRule

      service.create(
        CreateIssueTypeAccessRuleCommand(
          tenantId = tenantId,
          configApiId = configApiId,
          subjectType = WorkItemAccessSubjectType.ANYONE,
          actionType = WorkItemAccessActionType.TRANSITION,
          transitionId = transitionId,
          effect = PermissionEffect.ALLOW,
        )
      )

      coVerify { accessRules.create(any()) }
    }

    "create rejects unknown transition" {
      coEvery { workflows.listTransitions(tenantId, workflowId) } returns emptyList()

      shouldThrow<InvalidRequestException> {
          service.create(
            CreateIssueTypeAccessRuleCommand(
              tenantId = tenantId,
              configApiId = configApiId,
              subjectType = WorkItemAccessSubjectType.ANYONE,
              actionType = WorkItemAccessActionType.TRANSITION,
              transitionId = transitionId,
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
