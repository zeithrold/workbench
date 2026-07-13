package ink.doa.workbench.application.permission

import ink.doa.workbench.identity.permission.CreatePermissionActionCommand
import ink.doa.workbench.identity.permission.PermissionActionRepository
import ink.doa.workbench.identity.permission.model.AuthorizationAction
import org.springframework.stereotype.Service

@Service
class PermissionActionService(private val actions: PermissionActionRepository) {
  suspend fun listActions(): List<ActionView> = actions.list().map { ActionView.from(it) }

  suspend fun ensureAction(code: String, description: String?): ActionView =
    actions.upsert(CreatePermissionActionCommand(AuthorizationAction(code), description)).let {
      ActionView.from(it)
    }
}
