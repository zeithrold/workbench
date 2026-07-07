package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.errors.requireValid
import ink.doa.workbench.core.permission.PermissionGroupRepository
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkflowConfigurationRepository
import ink.doa.workbench.core.workitem.access.CreateWorkItemAccessRuleCommand
import ink.doa.workbench.core.workitem.access.WorkItemAccessActionType
import ink.doa.workbench.core.workitem.access.WorkItemAccessRuleRecord
import ink.doa.workbench.core.workitem.access.WorkItemAccessRuleRepository
import ink.doa.workbench.core.workitem.access.WorkItemAccessSubjectType
import ink.doa.workbench.core.workitem.query.WorkItemConditionJson
import ink.doa.workbench.core.workitem.query.WorkItemTransitionConditionValidator
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Service

@Service
class IssueTypeConfigAccessRuleService(
  private val configs: IssueTypeConfigRepository,
  private val accessRules: WorkItemAccessRuleRepository,
  private val workflows: WorkflowConfigurationRepository,
  private val groups: PermissionGroupRepository,
) {
  private val conditionValidator = WorkItemTransitionConditionValidator()

  suspend fun list(tenantId: UUID, configApiId: String): List<WorkItemAccessRuleRecord> {
    val config = requireConfig(tenantId, configApiId)
    return accessRules.listByConfig(tenantId, config.config.id)
  }

  suspend fun create(command: CreateIssueTypeAccessRuleCommand): WorkItemAccessRuleRecord {
    val config = requireConfig(command.tenantId, command.configApiId)
    validateSubject(command)
    validateAction(command, config.config.workflowId)
    val canonicalCondition = WorkItemConditionJson.canonicalize(command.condition)
    conditionValidator.validate(canonicalCondition)
    return accessRules.create(
      CreateWorkItemAccessRuleCommand(
        tenantId = command.tenantId,
        issueTypeConfigId = config.config.id,
        subjectType = command.subjectType,
        subjectUserId = command.subjectUserId,
        subjectGroupId = command.subjectGroupId,
        subjectRoleCode = command.subjectRoleCode,
        actionType = command.actionType,
        transitionId = command.transitionId,
        fieldKey = command.fieldKey,
        effect = command.effect,
        condition = canonicalCondition,
        rank = command.rank,
      )
    )
  }

  suspend fun deactivate(tenantId: UUID, configApiId: String, ruleApiId: String): Boolean {
    val config = requireConfig(tenantId, configApiId)
    val rule =
      accessRules.findByApiId(tenantId, ruleApiId)
        ?: throw ResourceNotFoundException(
          WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_CONFIG_NOT_FOUND
        )
    requireValid(
      rule.issueTypeConfigId == config.config.id,
      WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_CONFIG_NOT_FOUND,
    )
    return accessRules.deactivate(tenantId, rule.id)
  }

  private suspend fun requireConfig(tenantId: UUID, configApiId: String) =
    configs.findConfig(tenantId, configApiId)
      ?: throw ResourceNotFoundException(
        WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_CONFIG_NOT_FOUND
      )

  private suspend fun validateSubject(command: CreateIssueTypeAccessRuleCommand) {
    when (command.subjectType) {
      WorkItemAccessSubjectType.USER ->
        requireValid(command.subjectUserId != null, WorkbenchErrorCode.REQUEST_VALIDATION_FAILED)
      WorkItemAccessSubjectType.IN_GROUP,
      WorkItemAccessSubjectType.NOT_IN_GROUP -> {
        val groupId =
          command.subjectGroupId
            ?: throw InvalidRequestException(WorkbenchErrorCode.REQUEST_VALIDATION_FAILED)
        groups.findById(command.tenantId, groupId)
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PERMISSION_GROUP_NOT_FOUND)
      }
      WorkItemAccessSubjectType.IN_ROLE,
      WorkItemAccessSubjectType.NOT_IN_ROLE ->
        requireValid(
          !command.subjectRoleCode.isNullOrBlank(),
          WorkbenchErrorCode.REQUEST_VALIDATION_FAILED,
        )
      WorkItemAccessSubjectType.ANYONE -> Unit
    }
  }

  private suspend fun validateAction(command: CreateIssueTypeAccessRuleCommand, workflowId: UUID) {
    when (command.actionType) {
      WorkItemAccessActionType.TRANSITION -> {
        requireValid(command.transitionId != null, WorkbenchErrorCode.REQUEST_VALIDATION_FAILED)
        val transition =
          workflows.listTransitions(command.tenantId, workflowId).firstOrNull {
            it.id == command.transitionId
          }
            ?: throw InvalidRequestException(
              WorkbenchErrorCode.RESOURCE_WORKFLOW_TRANSITION_NOT_FOUND
            )
        requireValid(
          transition.workflowId == workflowId,
          WorkbenchErrorCode.RESOURCE_WORKFLOW_TRANSITION_NOT_FOUND,
        )
      }
      WorkItemAccessActionType.FIELD_WRITE ->
        requireValid(
          !command.fieldKey.isNullOrBlank(),
          WorkbenchErrorCode.REQUEST_VALIDATION_FAILED,
        )
      WorkItemAccessActionType.FIELD_WRITE_ALL,
      WorkItemAccessActionType.COMMENT -> Unit
    }
  }
}

data class CreateIssueTypeAccessRuleCommand(
  val tenantId: UUID,
  val configApiId: String,
  val subjectType: WorkItemAccessSubjectType,
  val subjectUserId: UUID? = null,
  val subjectGroupId: UUID? = null,
  val subjectRoleCode: String? = null,
  val actionType: WorkItemAccessActionType,
  val transitionId: UUID? = null,
  val fieldKey: String? = null,
  val effect: PermissionEffect,
  val condition: JsonObject = JsonObject(emptyMap()),
  val rank: Int = 100,
)
