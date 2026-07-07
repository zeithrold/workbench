package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.permission.PermissionBindingRepository
import ink.doa.workbench.core.permission.PermissionGroupRepository
import ink.doa.workbench.core.permission.PermissionPolicyRepository
import ink.doa.workbench.core.permission.PermissionPrincipalType
import ink.doa.workbench.core.workitem.access.WorkItemAccessActor
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.stereotype.Component

private val PROJECT_POLICY_TO_ROLE =
  mapOf(
    "project-admin" to "admin",
    "project-member" to "member",
    "project-viewer" to "viewer",
  )

@Component
class WorkItemAccessPrincipalResolver(
  private val bindings: PermissionBindingRepository,
  private val groups: PermissionGroupRepository,
  private val policies: PermissionPolicyRepository,
  private val clock: Clock,
) {
  suspend fun resolveActor(
    tenantId: UUID,
    projectId: UUID,
    actorUserId: UUID,
  ): WorkItemAccessActor =
    WorkItemAccessActor(
      userId = actorUserId,
      groupIds = groups.listActiveGroupIdsForUser(tenantId, actorUserId),
      projectRoles = resolveProjectRoles(tenantId, projectId, actorUserId),
    )

  private suspend fun resolveProjectRoles(
    tenantId: UUID,
    projectId: UUID,
    actorUserId: UUID,
  ): Set<String> {
    val at = now()
    val groupIds = groups.listActiveGroupIdsForUser(tenantId, actorUserId)
    return bindings
      .listByProject(tenantId, projectId)
      .filter { binding -> binding.isActiveAt(at) && binding.matchesActor(actorUserId, groupIds) }
      .mapNotNull { binding ->
        policies.findById(tenantId, binding.policyId)?.code?.let { PROJECT_POLICY_TO_ROLE[it] }
      }
      .toSet()
  }

  private fun now(): OffsetDateTime = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
}

private fun ink.doa.workbench.core.permission.PermissionBindingRecord.isActiveAt(
  at: OffsetDateTime
): Boolean {
  val expiresAt = validTo
  return validFrom <= at && (expiresAt == null || expiresAt > at)
}

private fun ink.doa.workbench.core.permission.PermissionBindingRecord.matchesActor(
  actorUserId: UUID,
  groupIds: Set<UUID>,
): Boolean =
  when (principalType) {
    PermissionPrincipalType.USER -> principalUserId == actorUserId
    PermissionPrincipalType.GROUP -> principalGroupId != null && principalGroupId in groupIds
    PermissionPrincipalType.TENANT_MEMBER -> true
  }
