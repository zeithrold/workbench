package ink.doa.workbench.application.permission

import ink.doa.workbench.identity.permission.AccessGrantRepository
import ink.doa.workbench.identity.permission.CreateAccessGrantCommand
import ink.doa.workbench.identity.permission.GrantScope
import ink.doa.workbench.identity.permission.model.AuthorizationAction
import java.time.OffsetDateTime
import java.util.UUID
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
        AuthorizationAction("outbox.read") to "outbox",
        AuthorizationAction("outbox.manage") to "outbox",
      )
  }
}
