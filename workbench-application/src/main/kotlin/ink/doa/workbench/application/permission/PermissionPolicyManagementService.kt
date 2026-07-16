package ink.doa.workbench.application.permission

import ink.doa.workbench.identity.permission.CreatePermissionPolicyCommand
import ink.doa.workbench.identity.permission.CreatePermissionPolicyRuleCommand
import ink.doa.workbench.identity.permission.PermissionActionRepository
import ink.doa.workbench.identity.permission.PermissionPolicyRecord
import ink.doa.workbench.identity.permission.PermissionPolicyRepository
import ink.doa.workbench.identity.permission.ReplacePermissionPolicyCommand
import ink.doa.workbench.identity.permission.ReplacePermissionPolicyRuleCommand
import ink.doa.workbench.identity.permission.UpdatePermissionPolicyCommand
import ink.doa.workbench.identity.permission.model.AuthorizationAction
import ink.doa.workbench.identity.permission.model.PermissionEffect
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.ResourceConflictException
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class PermissionPolicyManagementService(
  private val policies: PermissionPolicyRepository,
  private val clock: Clock,
  actions: PermissionActionRepository? = null,
) {
  private val documentValidator = PermissionPolicyDocumentValidator(actions)

  suspend fun listPolicies(tenantId: UUID): List<PermissionPolicyView> =
    policies.list(tenantId).mapNotNull { policy ->
      val rules = policies.listRules(policy.id)
      if (TenantPermissionCapabilities.isTenantPolicy(rules)) {
        PermissionPolicyView.from(policy, rules)
      } else {
        null
      }
    }

  suspend fun getPolicy(tenantId: UUID, publicId: String): PermissionPolicyView {
    val policy = requirePolicy(tenantId, publicId)
    val rules = policies.listRules(policy.id)
    requireTenantPolicy(rules)
    return PermissionPolicyView.from(policy, rules)
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

  suspend fun createDocument(command: CreatePermissionPolicyDocumentCommand): PermissionPolicyView {
    documentValidator.requireSchemaVersion(command.schemaVersion)
    documentValidator.requireTenantRules(command.rules)
    if (policies.findByCode(command.tenantId, command.code) != null) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_CODE_IN_USE)
    }
    val rules = documentValidator.validateRules(command.rules, emptyMap())
    val policy =
      policies.create(
        CreatePermissionPolicyCommand(
          tenantId = command.tenantId,
          code = command.code,
          name = command.name,
          description = command.description,
          rules =
            rules.map {
              CreatePermissionPolicyRuleCommand(
                policyId = UUID(0, 0),
                action = it.action,
                resourcePattern = it.resourcePattern,
                effect = it.effect,
                conditionJson = it.conditionJson,
                position = it.position,
              )
            },
        )
      )
    return PermissionPolicyView.from(policy, policies.listRules(policy.id))
  }

  suspend fun replaceDocument(
    command: ReplacePermissionPolicyDocumentCommand
  ): PermissionPolicyView {
    documentValidator.requireSchemaVersion(command.schemaVersion)
    val policy = requirePolicy(command.tenantId, command.policyPublicId)
    documentValidator.requireMutablePolicy(policy)
    documentValidator.requireUnchangedCode(policy, command.code)
    val existingRules = policies.listRules(policy.id).associateBy { it.apiId.value }
    requireTenantPolicy(existingRules.values.toList())
    documentValidator.requireTenantRules(command.rules)
    val rules = documentValidator.validateRules(command.rules, existingRules)
    val expectedRevision = documentValidator.parseRevision(command.revision)
    val updated =
      policies.replace(
        ReplacePermissionPolicyCommand(
          policyId = policy.id,
          expectedUpdatedAt = expectedRevision,
          name = command.name,
          description = command.description,
          rules = rules,
        )
      ) ?: throw ResourceConflictException(WorkbenchErrorCode.PERMISSION_POLICY_REVISION_CONFLICT)
    return PermissionPolicyView.from(updated, policies.listRules(policy.id))
  }

  suspend fun updatePolicy(
    tenantId: UUID,
    publicId: String,
    name: String?,
    description: String?,
  ): PermissionPolicyView {
    val policy = requirePolicy(tenantId, publicId)
    requireTenantPolicy(policies.listRules(policy.id))
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
    requireTenantPolicy(policies.listRules(policy.id))
    if (policy.builtin) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_BUILTIN_DELETE_FORBIDDEN)
    }
    if (policies.hasActiveBindings(policy.id, OffsetDateTime.now(clock))) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_ACTIVE_BINDINGS)
    }
    return policies.delete(tenantId, policy.id)
  }

  suspend fun addPolicyRule(command: AddPolicyRuleCommand): PermissionPolicyView {
    val policy = requirePolicy(command.tenantId, command.policyPublicId)
    requireTenantPolicy(policies.listRules(policy.id))
    if (policy.builtin) {
      throw InvalidRequestException(
        WorkbenchErrorCode.PERMISSION_POLICY_RULES_BUILTIN_CHANGE_FORBIDDEN
      )
    }
    documentValidator.requireTenantRule(
      PermissionPolicyDocumentRuleCommand(
        id = null,
        action = command.action,
        resourcePattern = command.resourcePattern,
        effect = command.effect,
        conditionJson = command.conditionJson,
      )
    )
    val canonicalCondition = PermissionConditionJson.validateAndCanonicalize(command.conditionJson)
    policies.addRule(
      CreatePermissionPolicyRuleCommand(
        policyId = policy.id,
        action = AuthorizationAction(command.action),
        resourcePattern = command.resourcePattern,
        effect = command.effect,
        conditionJson = canonicalCondition,
        position = policies.listRules(policy.id).size,
      )
    )
    return PermissionPolicyView.from(policy, policies.listRules(policy.id))
  }

  suspend fun requirePolicy(tenantId: UUID, publicId: String): PermissionPolicyRecord =
    policies.findByApiId(tenantId, publicId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PERMISSION_POLICY_NOT_FOUND)

  fun simulate(
    tenantId: UUID,
    schemaVersion: Int,
    rules: List<PermissionPolicyDocumentRuleCommand>,
    action: String,
  ): TenantPolicySimulation {
    documentValidator.requireSchemaVersion(schemaVersion)
    documentValidator.requireTenantRules(rules)
    val capability =
      TenantPermissionCapabilities.find(action)
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.PERMISSION_POLICY_TENANT_ACTION_FORBIDDEN
        )
    val traces = rules.mapIndexed { index, rule ->
      val matches =
        rule.action == capability.action && rule.resourcePattern == capability.resourcePattern
      TenantPolicySimulationRule(
        index = index,
        action = rule.action,
        effect = rule.effect,
        matches = matches,
        contributes = matches,
      )
    }
    return when {
      traces.any { it.contributes && it.effect == PermissionEffect.DENY } ->
        TenantPolicySimulation(tenantId, PermissionEffect.DENY, "matching_deny", traces)
      traces.any { it.contributes && it.effect == PermissionEffect.ALLOW } ->
        TenantPolicySimulation(tenantId, PermissionEffect.ALLOW, "matching_allow", traces)
      else -> TenantPolicySimulation(tenantId, PermissionEffect.DENY, "no_matching_allow", traces)
    }
  }

  private fun requireTenantPolicy(
    rules: List<ink.doa.workbench.identity.permission.PermissionPolicyRuleRecord>
  ) {
    if (!TenantPermissionCapabilities.isTenantPolicy(rules)) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_NOT_TENANT_POLICY)
    }
  }
}

private class PermissionPolicyDocumentValidator(private val actions: PermissionActionRepository?) {
  fun requireTenantRules(rules: List<PermissionPolicyDocumentRuleCommand>) {
    if (rules.isEmpty()) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_TENANT_RULE_REQUIRED)
    }
    rules.forEach(::requireTenantRule)
  }

  fun requireTenantRule(rule: PermissionPolicyDocumentRuleCommand) {
    val capability =
      TenantPermissionCapabilities.find(rule.action)
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.PERMISSION_POLICY_TENANT_ACTION_FORBIDDEN
        )
    if (rule.resourcePattern != capability.resourcePattern) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_TENANT_RESOURCE_MISMATCH)
    }
    if (!rule.conditionJson.isNullOrBlank()) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_TENANT_CONDITION_FORBIDDEN)
    }
  }

  suspend fun validateRules(
    rules: List<PermissionPolicyDocumentRuleCommand>,
    existingRules: Map<String, ink.doa.workbench.identity.permission.PermissionPolicyRuleRecord>,
  ): List<ReplacePermissionPolicyRuleCommand> {
    requireKnownRuleIds(rules.mapNotNull { it.id }, existingRules.keys)
    return rules.mapIndexed { position, rule -> validateRule(rule, position) }
  }

  private suspend fun validateRule(
    rule: PermissionPolicyDocumentRuleCommand,
    position: Int,
  ): ReplacePermissionPolicyRuleCommand {
    requireTenantRule(rule)
    val action = AuthorizationAction(rule.action)
    if (actions != null && actions.findByCode(action) == null) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_RULE_ACTION_UNKNOWN)
    }
    requireValidResourcePattern(rule.resourcePattern)
    return ReplacePermissionPolicyRuleCommand(
      apiId = rule.id,
      action = action,
      resourcePattern = rule.resourcePattern,
      effect = rule.effect,
      conditionJson = PermissionConditionJson.validateAndCanonicalize(rule.conditionJson),
      position = position,
    )
  }

  private fun requireKnownRuleIds(ids: List<String>, existingRuleIds: Set<String>) {
    if (ids.size != ids.toSet().size || ids.any { it !in existingRuleIds }) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_RULE_ID_INVALID)
    }
  }

  private fun requireValidResourcePattern(resourcePattern: String) {
    if (!resourcePatternRegex.matches(resourcePattern)) {
      throw InvalidRequestException(
        WorkbenchErrorCode.PERMISSION_POLICY_RULE_RESOURCE_PATTERN_INVALID
      )
    }
  }

  fun requireMutablePolicy(policy: PermissionPolicyRecord) {
    if (policy.builtin) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_BUILTIN_UPDATE_FORBIDDEN)
    }
  }

  fun requireUnchangedCode(policy: PermissionPolicyRecord, code: String) {
    if (policy.code != code) {
      throw InvalidRequestException(
        WorkbenchErrorCode.PERMISSION_POLICY_CODE_IN_USE,
        "Policy code cannot be changed after creation.",
      )
    }
  }

  fun parseRevision(revision: String): OffsetDateTime =
    runCatching { OffsetDateTime.parse(revision) }
      .getOrElse {
        throw InvalidRequestException(
          WorkbenchErrorCode.PERMISSION_POLICY_REVISION_CONFLICT,
          "Policy revision is invalid.",
        )
      }

  fun requireSchemaVersion(schemaVersion: Int) {
    if (schemaVersion != 1) {
      throw InvalidRequestException(
        WorkbenchErrorCode.PERMISSION_POLICY_RULE_CONDITION_INVALID,
        "Unsupported permission policy schemaVersion: $schemaVersion.",
      )
    }
  }

  private companion object {
    val resourcePatternRegex = Regex("^(\\*|[a-z][a-z0-9-]*(?::(?:\\*|[A-Za-z0-9._-]+))?)$")
  }
}
