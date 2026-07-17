package one.ztd.workbench.identity

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.identity.model.CreateLoginAccountCommand
import one.ztd.workbench.identity.model.LoginAccountParameterKey
import one.ztd.workbench.identity.model.LoginAccountParameterRecord
import one.ztd.workbench.identity.model.LoginAccountRecord
import one.ztd.workbench.identity.model.UpsertLoginAccountParameterCommand

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
