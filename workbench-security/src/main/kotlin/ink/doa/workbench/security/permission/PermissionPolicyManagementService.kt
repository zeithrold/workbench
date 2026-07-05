package ink.doa.workbench.security.permission

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.permission.CreatePermissionPolicyCommand
import ink.doa.workbench.core.permission.CreatePermissionPolicyRuleCommand
import ink.doa.workbench.core.permission.PermissionConditionJson
import ink.doa.workbench.core.permission.PermissionPolicyRecord
import ink.doa.workbench.core.permission.PermissionPolicyRepository
import ink.doa.workbench.core.permission.UpdatePermissionPolicyCommand
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.PermissionEffect
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class PermissionPolicyManagementService(
  private val policies: PermissionPolicyRepository,
  private val clock: Clock,
) {
  suspend fun listPolicies(tenantId: UUID): List<PermissionPolicyView> =
    policies.list(tenantId).map { policy ->
      PermissionPolicyView.from(policy, policies.listRules(policy.id))
    }

  suspend fun getPolicy(tenantId: UUID, publicId: String): PermissionPolicyView {
    val policy = requirePolicy(tenantId, publicId)
    return PermissionPolicyView.from(policy, policies.listRules(policy.id))
  }

  suspend fun createPolicy(
    tenantId: UUID,
    code: String,
    name: String,
    description: String?,
  ): PermissionPolicyView {
    if (policies.findByCode(tenantId, code) != null) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_CODE_IN_USE)
    }
    val policy =
      policies.create(
        CreatePermissionPolicyCommand(
          tenantId = tenantId,
          code = code,
          name = name,
          description = description,
          builtin = false,
        )
      )
    return PermissionPolicyView.from(policy, emptyList())
  }

  suspend fun updatePolicy(
    tenantId: UUID,
    publicId: String,
    name: String?,
    description: String?,
  ): PermissionPolicyView {
    val policy = requirePolicy(tenantId, publicId)
    if (policy.builtin) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_BUILTIN_UPDATE_FORBIDDEN)
    }
    return PermissionPolicyView.from(
      policies.update(UpdatePermissionPolicyCommand(policy.id, name, description)),
      policies.listRules(policy.id),
    )
  }

  suspend fun deletePolicy(tenantId: UUID, publicId: String): Boolean {
    val policy = requirePolicy(tenantId, publicId)
    if (policy.builtin) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_BUILTIN_DELETE_FORBIDDEN)
    }
    if (policies.hasActiveBindings(policy.id, OffsetDateTime.now(clock))) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_ACTIVE_BINDINGS)
    }
    return policies.delete(tenantId, policy.id)
  }

  suspend fun addPolicyRule(
    tenantId: UUID,
    policyPublicId: String,
    action: String,
    resourcePattern: String,
    effect: PermissionEffect,
    conditionJson: String? = null,
  ): PermissionPolicyView {
    val policy = requirePolicy(tenantId, policyPublicId)
    if (policy.builtin) {
      throw InvalidRequestException(
        WorkbenchErrorCode.PERMISSION_POLICY_RULES_BUILTIN_CHANGE_FORBIDDEN
      )
    }
    val canonicalCondition = PermissionConditionJson.validateAndCanonicalize(conditionJson)
    policies.addRule(
      CreatePermissionPolicyRuleCommand(
        policyId = policy.id,
        action = AuthorizationAction(action),
        resourcePattern = resourcePattern,
        effect = effect,
        conditionJson = canonicalCondition,
      )
    )
    return PermissionPolicyView.from(policy, policies.listRules(policy.id))
  }

  suspend fun requirePolicy(tenantId: UUID, publicId: String): PermissionPolicyRecord =
    policies.findByApiId(tenantId, publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PERMISSION_POLICY_NOT_FOUND)
}
