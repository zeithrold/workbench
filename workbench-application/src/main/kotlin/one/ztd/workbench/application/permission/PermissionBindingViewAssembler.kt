package one.ztd.workbench.application.permission

import java.util.UUID
import one.ztd.workbench.agile.project.ProjectRepository
import one.ztd.workbench.agile.project.ProjectSummary
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.identity.permission.PermissionBindingRecord
import one.ztd.workbench.identity.permission.PermissionGroupRepository
import one.ztd.workbench.identity.permission.PermissionPolicyRepository
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import org.springframework.stereotype.Component

@Component
class PermissionBindingViewAssembler(
  private val groups: PermissionGroupRepository,
  private val policies: PermissionPolicyRepository,
  private val users: UserRepository,
  private val projects: ProjectRepository,
) {
  suspend fun assemble(
    tenantId: UUID,
    binding: PermissionBindingRecord,
  ): PermissionBindingView {
    val policy = requirePolicyRecord(tenantId, binding.policyId)
    val user = binding.principalUserId?.let { UserSummary.from(requireUser(it)) }
    val group = binding.principalGroupId?.let { requireGroupView(tenantId, it) }
    val project = binding.projectId?.let { requireProjectSummary(tenantId, it) }
    return PermissionBindingView(
      id = binding.apiId.value,
      principalType = binding.principalType.name,
      user = user,
      group = group,
      policy = PermissionPolicySummary.from(policy),
      project = project,
      validFrom = binding.validFrom,
      validTo = binding.validTo,
    )
  }

  private suspend fun requirePolicyRecord(tenantId: UUID, policyId: UUID) =
    policies.findById(tenantId, policyId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PERMISSION_POLICY_NOT_FOUND)

  private suspend fun requireGroupView(tenantId: UUID, groupId: UUID): PermissionGroupView =
    groups.findById(tenantId, groupId)?.let { PermissionGroupView.from(it) }
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PERMISSION_GROUP_NOT_FOUND)

  private suspend fun requireProjectSummary(tenantId: UUID, projectId: UUID): ProjectSummary =
    projects.findById(tenantId, projectId)?.let(ProjectSummary::from)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)

  private suspend fun requireUser(userId: UUID) =
    users.findById(userId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)
}
