package one.ztd.workbench.application.permission

import java.util.UUID
import one.ztd.workbench.identity.permission.PermissionPolicyRecord
import one.ztd.workbench.identity.permission.PermissionPolicyRepository
import one.ztd.workbench.identity.permission.PermissionPolicyRuleRecord
import one.ztd.workbench.identity.permission.model.PermissionEffect
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import org.springframework.stereotype.Service

data class TenantPermissionCapability(
  val action: String,
  val resourcePattern: String,
  val name: String,
  val description: String,
)

object TenantPermissionCapabilities {
  val all =
    listOf(
      TenantPermissionCapability(
        "tenant.access",
        "tenant:*",
        "Access tenant",
        "Access tenant-scoped Workbench features.",
      ),
      TenantPermissionCapability(
        "tenant.read",
        "tenant:*",
        "View tenant settings",
        "View tenant metadata and management information.",
      ),
      TenantPermissionCapability(
        "tenant.update",
        "tenant:*",
        "Update tenant settings",
        "Change tenant name, slug, locale, and timezone.",
      ),
      TenantPermissionCapability(
        "tenant.member.manage",
        "tenant:*",
        "Manage tenant members",
        "Invite, suspend, restore, and remove tenant members.",
      ),
      TenantPermissionCapability(
        "permission.group.manage",
        "permission:*",
        "Manage permission groups",
        "Create groups and manage their tenant members.",
      ),
      TenantPermissionCapability(
        "permission.assignment.manage",
        "permission:*",
        "Assign tenant permissions",
        "Bind tenant policies to users, groups, or all tenant members.",
      ),
      TenantPermissionCapability(
        "permission.policy.manage",
        "permission:*",
        "Manage tenant policies",
        "Create and update tenant-level custom permission policies.",
      ),
    )

  private val byAction = all.associateBy { it.action }

  fun find(action: String): TenantPermissionCapability? = byAction[action]

  fun matches(rule: PermissionPolicyRuleRecord): Boolean =
    find(rule.action.code)?.resourcePattern == rule.resourcePattern && rule.conditionJson == null

  fun isTenantPolicy(rules: List<PermissionPolicyRuleRecord>): Boolean =
    rules.isNotEmpty() && rules.all(::matches)
}

data class TenantPolicySimulationRule(
  val index: Int,
  val action: String,
  val effect: PermissionEffect,
  val matches: Boolean,
  val contributes: Boolean,
)

data class TenantPolicySimulation(
  val tenantId: UUID,
  val decision: PermissionEffect,
  val reason: String,
  val rules: List<TenantPolicySimulationRule>,
)

@Service
class TenantPermissionPolicyGuard(private val policies: PermissionPolicyRepository) {
  suspend fun requirePolicy(tenantId: UUID, publicId: String): PermissionPolicyRecord {
    val policy =
      policies.findByApiId(tenantId, publicId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PERMISSION_POLICY_NOT_FOUND)
    if (!isTenantPolicy(policy)) {
      throw InvalidRequestException(WorkbenchErrorCode.PERMISSION_POLICY_NOT_TENANT_POLICY)
    }
    return policy
  }

  suspend fun contains(tenantId: UUID, policyId: UUID): Boolean {
    val policy = policies.findById(tenantId, policyId) ?: return false
    return isTenantPolicy(policy)
  }

  private suspend fun isTenantPolicy(policy: PermissionPolicyRecord): Boolean =
    TenantPermissionCapabilities.isTenantPolicy(policies.listRules(policy.id))
}
