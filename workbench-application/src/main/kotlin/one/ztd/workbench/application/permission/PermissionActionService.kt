package one.ztd.workbench.application.permission

import one.ztd.workbench.identity.permission.CreatePermissionActionCommand
import one.ztd.workbench.identity.permission.PermissionActionRepository
import one.ztd.workbench.identity.permission.model.AuthorizationAction
import org.springframework.stereotype.Service

@Service
class PermissionActionService(private val actions: PermissionActionRepository) {
  suspend fun listActions(): List<ActionView> = actions.list().map { ActionView.from(it) }

  suspend fun listTenantCapabilities(): List<TenantPermissionCapability> {
    val registered = actions.list().map { it.code.code }.toSet()
    return TenantPermissionCapabilities.all.filter { it.action in registered }
  }

  suspend fun ensureAction(code: String, description: String?): ActionView =
    actions.upsert(CreatePermissionActionCommand(AuthorizationAction(code), description)).let {
      ActionView.from(it)
    }
}
