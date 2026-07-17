package one.ztd.workbench.identity

import java.util.UUID
import one.ztd.workbench.identity.model.CreateLoginMethodDefinitionCommand
import one.ztd.workbench.identity.model.LoginMethodDefinitionRecord

interface LoginMethodRepository {
  suspend fun createLoginMethod(
    command: CreateLoginMethodDefinitionCommand
  ): LoginMethodDefinitionRecord

  suspend fun findLoginMethodByCode(code: String): LoginMethodDefinitionRecord?

  suspend fun findLoginMethodByApiId(apiId: String): LoginMethodDefinitionRecord?

  suspend fun findLoginMethodById(id: UUID): LoginMethodDefinitionRecord?
}
