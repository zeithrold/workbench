package ink.doa.workbench.core.identity

import ink.doa.workbench.core.identity.model.CreateLoginMethodDefinitionCommand
import ink.doa.workbench.core.identity.model.LoginMethodDefinitionRecord
import java.util.UUID

interface LoginMethodRepository {
  suspend fun createLoginMethod(
    command: CreateLoginMethodDefinitionCommand
  ): LoginMethodDefinitionRecord

  suspend fun findLoginMethodByCode(code: String): LoginMethodDefinitionRecord?

  suspend fun findLoginMethodByApiId(apiId: String): LoginMethodDefinitionRecord?

  suspend fun findLoginMethodById(id: UUID): LoginMethodDefinitionRecord?
}
