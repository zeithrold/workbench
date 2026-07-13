package ink.doa.workbench.identity

import ink.doa.workbench.identity.model.CreateLoginAccountCommand
import ink.doa.workbench.identity.model.LoginAccountParameterKey
import ink.doa.workbench.identity.model.LoginAccountParameterRecord
import ink.doa.workbench.identity.model.LoginAccountRecord
import ink.doa.workbench.identity.model.UpsertLoginAccountParameterCommand
import java.time.OffsetDateTime
import java.util.UUID

interface LoginAccountStore {
  suspend fun createLoginAccount(command: CreateLoginAccountCommand): LoginAccountRecord

  suspend fun upsertParameter(
    command: UpsertLoginAccountParameterCommand
  ): LoginAccountParameterRecord

  suspend fun findParameter(
    loginAccountId: UUID,
    parameterKey: LoginAccountParameterKey,
  ): LoginAccountParameterRecord?

  suspend fun findLoginAccountByMethodAndSubject(
    loginMethodCode: String,
    normalizedSubject: String,
  ): LoginAccountRecord?

  suspend fun findLoginAccountByParameterValue(
    loginMethodCode: String,
    parameterKey: LoginAccountParameterKey,
    parameterValue: String,
  ): LoginAccountRecord?

  suspend fun touchLastUsed(loginAccountId: UUID, usedAt: OffsetDateTime): Boolean
}
