package ink.doa.workbench.security.permission

import ink.doa.workbench.core.permission.AccessGrantRepository
import ink.doa.workbench.core.permission.CreateAccessGrantCommand
import ink.doa.workbench.core.permission.GrantScope
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.security.common.PublicIdResolver
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class AccessGrantManagementService(
  private val accessGrants: AccessGrantRepository,
  private val publicIds: PublicIdResolver,
  private val clock: Clock,
) {
  suspend fun listGrants(
    scope: GrantScope?,
    tenantId: UUID?,
    subjectUserId: UUID?,
  ): List<AccessGrantView> {
    val grants =
      when {
        scope == GrantScope.INSTANCE -> accessGrants.listInstanceGrants()
        tenantId != null -> accessGrants.listByTenant(tenantId)
        subjectUserId != null -> accessGrants.listBySubject(subjectUserId, scope, tenantId, null)
        else -> emptyList()
      }
    return grants.map { AccessGrantView.from(it) }
  }

  suspend fun createGrant(
    scope: GrantScope,
    tenantId: UUID?,
    userPublicId: String,
    actionCode: String,
    resourcePattern: String,
    effect: PermissionEffect,
    projectPublicId: String?,
    actorUserId: UUID?,
  ): AccessGrantView {
    val user = publicIds.resolveUser(userPublicId)
    val projectId = projectPublicId?.let { publicId ->
      require(tenantId != null) { "tenantId is required for project-scoped grants." }
      publicIds.resolveProject(tenantId, publicId).id
    }
    val record =
      accessGrants.create(
        CreateAccessGrantCommand(
          scope = scope,
          tenantId = tenantId,
          projectId = projectId,
          subjectUserId = user.id,
          action = AuthorizationAction(actionCode),
          resourcePattern = resourcePattern,
          effect = effect,
          validFrom = now(),
          grantedBy = actorUserId,
        )
      )
    return AccessGrantView.from(record)
  }

  suspend fun expireGrant(publicId: String): Boolean {
    val grant = accessGrants.findByApiId(publicId) ?: return false
    return accessGrants.expire(grant.id, now())
  }

  private fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}
