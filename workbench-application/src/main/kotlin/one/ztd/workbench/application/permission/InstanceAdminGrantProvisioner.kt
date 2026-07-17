package one.ztd.workbench.application.permission

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.identity.permission.AccessGrantRepository
import one.ztd.workbench.identity.permission.CreateAccessGrantCommand
import one.ztd.workbench.identity.permission.GrantScope
import one.ztd.workbench.identity.permission.model.AuthorizationAction
import org.springframework.stereotype.Component

/** Explicit capabilities for instance administrators. Identity alone grants nothing. */
@Component
class InstanceAdminGrantProvisioner(private val accessGrants: AccessGrantRepository) {
  suspend fun provision(userId: UUID, grantedBy: UUID?, validFrom: OffsetDateTime) {
    INSTANCE_GRANTS.forEach { (action, resource) ->
      accessGrants.create(
        CreateAccessGrantCommand(
          scope = GrantScope.INSTANCE,
          subjectUserId = userId,
          action = action,
          resourcePattern = resource,
          validFrom = validFrom,
          grantedBy = grantedBy,
        )
      )
    }
  }

  companion object {
    private val INSTANCE_GRANTS =
      listOf(
        AuthorizationAction("instance.read") to "instance:*",
        AuthorizationAction("instance.admin.manage") to "instance-admin:*",
        AuthorizationAction("tenant.create") to "tenant:*",
        AuthorizationAction("tenant.read") to "tenant:*",
        AuthorizationAction("tenant.update") to "tenant:*",
        AuthorizationAction("tenant.delete") to "tenant:*",
        AuthorizationAction("operations.read") to "operations:*",
        AuthorizationAction("outbox.read") to "outbox:*",
        AuthorizationAction("outbox.manage") to "outbox:*",
      )
  }
}
