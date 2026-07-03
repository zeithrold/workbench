package doa.ink.workbench.core.identity

import doa.ink.workbench.core.identity.model.CreateLoginAccountCommand
import doa.ink.workbench.core.identity.model.LoginAccountParameterKey
import doa.ink.workbench.core.identity.model.LoginAccountParameterRecord
import doa.ink.workbench.core.identity.model.LoginAccountRecord
import doa.ink.workbench.core.identity.model.UpsertLoginAccountParameterCommand
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
