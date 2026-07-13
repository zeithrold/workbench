package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.workitem.access.CreateWorkItemAccessRuleCommand
import ink.doa.workbench.agile.workitem.access.WorkItemAccessActionType
import ink.doa.workbench.agile.workitem.access.WorkItemAccessRuleRecord
import ink.doa.workbench.agile.workitem.access.WorkItemAccessRuleRepository
import ink.doa.workbench.agile.workitem.access.WorkItemAccessSubjectType
import ink.doa.workbench.agile.workitem.query.WorkItemConditionJson
import ink.doa.workbench.agile.workitem.query.WorkItemTransitionConditionValidator
import ink.doa.workbench.identity.UserRepository
import ink.doa.workbench.identity.permission.PermissionGroupRepository
import ink.doa.workbench.identity.permission.model.PermissionEffect
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.kernel.common.errors.requireValid
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Service

@Service
class IssueTypeConfigAccessRuleService(
  private val configs: IssueTypeConfigRepository,
  private val accessRules: WorkItemAccessRuleRepository,
  private val workflows: WorkflowConfigurationRepository,
  private val groups: PermissionGroupRepository,
  private val users: UserRepository,
) {
  private val conditionValidator = WorkItemTransitionConditionValidator()

  suspend fun list(tenantId: UUID, configApiId: String): List<WorkItemAccessRulePresentation> {
    val config = requireConfig(tenantId, configApiId)
    val transitions =
      workflows.listTransitions(tenantId, config.config.workflowId).associateBy { it.id }
    return accessRules.listByConfig(tenantId, config.config.id).map {
      present(tenantId, it, transitions)
    }
  }

  suspend fun create(command: CreateIssueTypeAccessRuleCommand): WorkItemAccessRulePresentation {
    val config = requireConfig(command.tenantId, command.configApiId)
    val resolved = resolveReferences(command)
    validateSubject(command, resolved)
    validateAction(command, resolved.transitionId, config.config.workflowId)
    val canonicalCondition = WorkItemConditionJson.canonicalize(command.condition)
    conditionValidator.validate(canonicalCondition)
    val record =
      accessRules.create(
        CreateWorkItemAccessRuleCommand(
          tenantId = command.tenantId,
          issueTypeConfigId = config.config.id,
          subjectType = command.subjectType,
          subjectUserId = resolved.subjectUserId,
          subjectGroupId = resolved.subjectGroupId,
          subjectRoleCode = command.subjectRoleCode,
          actionType = command.actionType,
          transitionId = resolved.transitionId,
          fieldKey = command.fieldKey,
          effect = command.effect,
          condition = canonicalCondition,
          rank = command.rank,
        )
      )
    val transitions =
      workflows.listTransitions(command.tenantId, config.config.workflowId).associateBy { it.id }
    return present(command.tenantId, record, transitions)
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

  private suspend fun present(
    tenantId: UUID,
    record: WorkItemAccessRuleRecord,
    transitions: Map<UUID, ink.doa.workbench.agile.workitem.model.WorkflowTransitionRecord> =
      emptyMap(),
  ): WorkItemAccessRulePresentation =
    WorkItemAccessRulePresentation(
      id = record.apiId.value,
      subjectType = record.subjectType.dbValue,
      subjectUserId = record.subjectUserId?.let { users.findById(it)?.apiId?.value },
      subjectGroupId = record.subjectGroupId?.let { groups.findById(tenantId, it)?.apiId?.value },
      subjectRoleCode = record.subjectRoleCode,
      actionType = record.actionType.dbValue,
      transitionId = record.transitionId?.let { transitions[it]?.apiId?.value },
      fieldKey = record.fieldKey,
      effect = record.effect.name.lowercase(),
      condition = record.condition,
      rank = record.rank,
    )

  private suspend fun resolveReferences(
    command: CreateIssueTypeAccessRuleCommand
  ): ResolvedAccessRuleReferences =
    ResolvedAccessRuleReferences(
      subjectUserId = command.subjectUserId?.let { resolveUser(it).id },
      subjectGroupId = command.subjectGroupId?.let { resolveGroup(command.tenantId, it).id },
      transitionId = command.transitionId?.let { resolveTransition(command.tenantId, it).id },
      transitionApiId = command.transitionId,
      subjectUserApiId = command.subjectUserId,
      subjectGroupApiId = command.subjectGroupId,
    )

  private suspend fun resolveUser(apiId: String) =
    users.findByApiId(apiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)

  private suspend fun resolveGroup(tenantId: UUID, apiId: String) =
    groups.findByApiId(tenantId, apiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PERMISSION_GROUP_NOT_FOUND)

  private suspend fun resolveTransition(tenantId: UUID, apiId: String) =
    workflows.findTransition(tenantId, apiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORKFLOW_TRANSITION_NOT_FOUND)

  private suspend fun requireConfig(tenantId: UUID, configApiId: String) =
    configs.findConfig(tenantId, configApiId)
      ?: throw ResourceNotFoundException(
        WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_CONFIG_NOT_FOUND
      )

  private suspend fun validateSubject(
    command: CreateIssueTypeAccessRuleCommand,
    resolved: ResolvedAccessRuleReferences,
  ) {
    when (command.subjectType) {
      WorkItemAccessSubjectType.USER ->
        requireValid(resolved.subjectUserId != null, WorkbenchErrorCode.REQUEST_VALIDATION_FAILED)
      WorkItemAccessSubjectType.IN_GROUP,
      WorkItemAccessSubjectType.NOT_IN_GROUP ->
        requireValid(resolved.subjectGroupId != null, WorkbenchErrorCode.REQUEST_VALIDATION_FAILED)
      WorkItemAccessSubjectType.IN_ROLE,
      WorkItemAccessSubjectType.NOT_IN_ROLE ->
        requireValid(
          !command.subjectRoleCode.isNullOrBlank(),
          WorkbenchErrorCode.REQUEST_VALIDATION_FAILED,
        )
      WorkItemAccessSubjectType.ANYONE -> Unit
    }
    if (resolved.subjectGroupId != null) {
      groups.findById(command.tenantId, resolved.subjectGroupId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PERMISSION_GROUP_NOT_FOUND)
    }
  }

  private suspend fun validateAction(
    command: CreateIssueTypeAccessRuleCommand,
    transitionId: UUID?,
    workflowId: UUID,
  ) {
    when (command.actionType) {
      WorkItemAccessActionType.TRANSITION -> {
        requireValid(transitionId != null, WorkbenchErrorCode.REQUEST_VALIDATION_FAILED)
        val transition =
          workflows.listTransitions(command.tenantId, workflowId).firstOrNull {
            it.id == transitionId
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
  val subjectUserId: String? = null,
  val subjectGroupId: String? = null,
  val subjectRoleCode: String? = null,
  val actionType: WorkItemAccessActionType,
  val transitionId: String? = null,
  val fieldKey: String? = null,
  val effect: PermissionEffect,
  val condition: JsonObject = JsonObject(emptyMap()),
  val rank: Int = 100,
)

data class WorkItemAccessRulePresentation(
  val id: String,
  val subjectType: String,
  val subjectUserId: String?,
  val subjectGroupId: String?,
  val subjectRoleCode: String?,
  val actionType: String,
  val transitionId: String?,
  val fieldKey: String?,
  val effect: String,
  val condition: JsonObject,
  val rank: Int,
)

private data class ResolvedAccessRuleReferences(
  val subjectUserId: UUID?,
  val subjectGroupId: UUID?,
  val transitionId: UUID?,
  val subjectUserApiId: String?,
  val subjectGroupApiId: String?,
  val transitionApiId: String?,
)
