package doa.ink.workbench.service.permission

import doa.ink.workbench.core.permission.CreatePermissionActionCommand
import doa.ink.workbench.core.permission.PermissionActionRepository
import doa.ink.workbench.core.permission.model.AuthorizationAction
import org.springframework.stereotype.Service

@Service
class PermissionActionService(private val actions: PermissionActionRepository) {
  suspend fun listActions(): List<ActionView> = actions.list().map { ActionView.from(it) }

  suspend fun ensureAction(code: String, description: String?): ActionView =
    actions.upsert(CreatePermissionActionCommand(AuthorizationAction(code), description)).let {
      ActionView.from(it)
    }
}
