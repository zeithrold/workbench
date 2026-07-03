package doa.ink.workbench.security.permission

import doa.ink.workbench.core.permission.AddGroupMemberCommand
import doa.ink.workbench.core.permission.CreatePermissionBindingCommand
import doa.ink.workbench.core.permission.CreatePermissionGroupCommand
import doa.ink.workbench.core.permission.CreatePermissionPolicyCommand
import doa.ink.workbench.core.permission.CreatePermissionPolicyRuleCommand
import doa.ink.workbench.core.permission.PermissionBindingRepository
import doa.ink.workbench.core.permission.PermissionGroupRepository
import doa.ink.workbench.core.permission.PermissionPolicyRecord
import doa.ink.workbench.core.permission.PermissionPolicyRepository
import doa.ink.workbench.core.permission.PermissionPrincipalType
import doa.ink.workbench.core.permission.model.AuthorizationAction
import doa.ink.workbench.core.permission.model.PermissionEffect
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

private const val TENANT_ADMIN_GROUP = "tenant-admin"
private const val TENANT_ADMIN_POLICY = "tenant-admin"

private val TENANT_ADMIN_RULES =
  listOf(
    "tenant.access" to "tenant:*",
    "tenant.read" to "tenant:*",
    "tenant.update" to "tenant:*",
    "tenant.member.manage" to "tenant:*",
    "project.create" to "project:*",
    "project.read" to "project:*",
    "project.update" to "project:*",
    "project.delete" to "project:*",
    "project.manage" to "project:*",
    "project.archive" to "project:*",
    "permission.group.manage" to "permission:*",
    "permission.assignment.manage" to "permission:*",
    "permission.policy.manage" to "permission:*",
  )

private val BUILTIN_POLICY_TEMPLATES =
  listOf(
    BuiltinPolicyTemplate(
      code = "project-admin",
      name = "Project Admin",
      description = "Manage assigned projects.",
      rules =
        listOf(
          "project.read" to "project:*",
          "project.update" to "project:*",
          "project.delete" to "project:*",
          "project.manage" to "project:*",
          "project.archive" to "project:*",
        ),
    ),
    BuiltinPolicyTemplate(
      code = "project-member",
      name = "Project Member",
      description = "Work inside assigned projects.",
      rules = listOf("project.read" to "project:*", "project.update" to "project:*"),
    ),
    BuiltinPolicyTemplate(
      code = "project-viewer",
      name = "Project Viewer",
      description = "Read assigned projects.",
      rules = listOf("project.read" to "project:*"),
    ),
  )

private data class BuiltinPolicyTemplate(
  val code: String,
  val name: String,
  val description: String,
  val rules: List<Pair<String, String>>,
)

@Service
class PermissionBootstrapService(
  private val groups: PermissionGroupRepository,
  private val policies: PermissionPolicyRepository,
  private val bindings: PermissionBindingRepository,
  private val clock: Clock,
) {
  suspend fun provisionTenantAdmin(
    tenantId: UUID,
    userId: UUID,
    actorUserId: UUID?,
  ) {
    val adminGroup =
      groups.findByCode(tenantId, TENANT_ADMIN_GROUP)
        ?: groups.create(
          CreatePermissionGroupCommand(
            tenantId = tenantId,
            code = TENANT_ADMIN_GROUP,
            name = "Tenant Admin",
            description = "Built-in tenant administrators.",
            builtin = true,
          )
        )
    groups.addMember(AddGroupMemberCommand(groupId = adminGroup.id, userId = userId))

    val adminPolicy =
      policies.findByCode(tenantId, TENANT_ADMIN_POLICY) ?: createTenantAdminPolicy(tenantId)
    BUILTIN_POLICY_TEMPLATES.forEach { ensurePolicyTemplate(tenantId, it) }

    val adminBindingExists =
      bindings.listByTenant(tenantId).any {
        it.principalType == PermissionPrincipalType.GROUP &&
          it.principalGroupId == adminGroup.id &&
          it.policyId == adminPolicy.id &&
          it.projectId == null &&
          it.validTo == null
      }
    if (!adminBindingExists) {
      bindings.create(
        CreatePermissionBindingCommand(
          tenantId = tenantId,
          projectId = null,
          principalType = PermissionPrincipalType.GROUP,
          principalUserId = null,
          principalGroupId = adminGroup.id,
          policyId = adminPolicy.id,
          validFrom = OffsetDateTime.now(clock),
          createdBy = actorUserId,
        )
      )
    }
  }

  suspend fun provisionProjectCreator(
    tenantId: UUID,
    projectId: UUID,
    userId: UUID,
    actorUserId: UUID?,
  ) {
    val adminPolicy =
      policies.findByCode(tenantId, "project-admin")
        ?: error("Built-in project-admin policy is not configured.")
    bindings.create(
      CreatePermissionBindingCommand(
        tenantId = tenantId,
        projectId = projectId,
        principalType = PermissionPrincipalType.USER,
        principalUserId = userId,
        principalGroupId = null,
        policyId = adminPolicy.id,
        validFrom = OffsetDateTime.now(clock),
        createdBy = actorUserId,
      )
    )
  }

  private suspend fun createTenantAdminPolicy(tenantId: UUID): PermissionPolicyRecord {
    val policy =
      policies.create(
        CreatePermissionPolicyCommand(
          tenantId = tenantId,
          code = TENANT_ADMIN_POLICY,
          name = "Tenant Admin",
          description = "Full tenant management permissions.",
          builtin = true,
        )
      )
    TENANT_ADMIN_RULES.forEach { (action, pattern) ->
      policies.addRule(
        CreatePermissionPolicyRuleCommand(
          policyId = policy.id,
          action = AuthorizationAction(action),
          resourcePattern = pattern,
          effect = PermissionEffect.ALLOW,
        )
      )
    }
    return policy
  }

  private suspend fun ensurePolicyTemplate(
    tenantId: UUID,
    template: BuiltinPolicyTemplate,
  ) {
    if (policies.findByCode(tenantId, template.code) != null) return
    val policy =
      policies.create(
        CreatePermissionPolicyCommand(
          tenantId = tenantId,
          code = template.code,
          name = template.name,
          description = template.description,
          builtin = true,
        )
      )
    template.rules.forEach { (action, pattern) ->
      policies.addRule(
        CreatePermissionPolicyRuleCommand(
          policyId = policy.id,
          action = AuthorizationAction(action),
          resourcePattern = pattern,
          effect = PermissionEffect.ALLOW,
        )
      )
    }
  }
}
