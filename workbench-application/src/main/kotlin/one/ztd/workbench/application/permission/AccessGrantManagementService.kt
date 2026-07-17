package one.ztd.workbench.application.permission

import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.application.identity.PublicIdResolver
import one.ztd.workbench.identity.permission.AccessGrantRepository
import one.ztd.workbench.identity.permission.CreateAccessGrantCommand
import one.ztd.workbench.identity.permission.GrantScope
import one.ztd.workbench.identity.permission.model.AuthorizationAction
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

  suspend fun createGrant(command: CreateManagedAccessGrantCommand): AccessGrantView {
    val user = publicIds.resolveUser(command.userPublicId)
    val projectId =
      command.projectPublicId?.let { publicId ->
        val tenantId =
          requireNotNull(command.tenantId) {
            "tenantId is required for project-scoped grants."
          }
        publicIds.resolveProject(tenantId, publicId).id
      }
    val record =
      accessGrants.create(
        CreateAccessGrantCommand(
          scope = command.scope,
          tenantId = command.tenantId,
          projectId = projectId,
          subjectUserId = user.id,
          action = AuthorizationAction(command.actionCode),
          resourcePattern = command.resourcePattern,
          effect = command.effect,
          validFrom = now(),
          grantedBy = command.actorUserId,
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
