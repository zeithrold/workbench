package one.ztd.workbench.agile.workitem

import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.agile.workitem.access.WorkItemAccessActor
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.identity.permission.PermissionBindingRepository
import one.ztd.workbench.identity.permission.PermissionGroupRepository
import one.ztd.workbench.identity.permission.PermissionPolicyRepository
import one.ztd.workbench.identity.permission.PermissionPrincipalType
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
  private val users: UserRepository,
  private val clock: Clock,
) {
  suspend fun resolveActor(
    tenantId: UUID,
    projectId: UUID,
    actorUserId: UUID,
  ): WorkItemAccessActor {
    val userApiId =
      users.findById(actorUserId)?.apiId?.value ?: error("User not found for actor: $actorUserId")
    return WorkItemAccessActor(
      userId = actorUserId,
      userApiId = userApiId,
      groupIds = groups.listActiveGroupIdsForUser(tenantId, actorUserId),
      projectRoles = resolveProjectRoles(tenantId, projectId, actorUserId),
    )
  }

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

private fun one.ztd.workbench.identity.permission.PermissionBindingRecord.isActiveAt(
  at: OffsetDateTime
): Boolean {
  val expiresAt = validTo
  return validFrom <= at && (expiresAt == null || expiresAt > at)
}

private fun one.ztd.workbench.identity.permission.PermissionBindingRecord.matchesActor(
  actorUserId: UUID,
  groupIds: Set<UUID>,
): Boolean =
  when (principalType) {
    PermissionPrincipalType.USER -> principalUserId == actorUserId
    PermissionPrincipalType.GROUP -> principalGroupId != null && principalGroupId in groupIds
    PermissionPrincipalType.TENANT_MEMBER -> true
  }
