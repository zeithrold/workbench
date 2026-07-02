package doa.ink.workbench.data.identity

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.identity.model.AuthEventRecord
import doa.ink.workbench.core.identity.model.AuthLoginStateRecord
import doa.ink.workbench.core.identity.model.AuthSessionRecord
import doa.ink.workbench.core.identity.model.BearerTokenRecord
import doa.ink.workbench.core.identity.model.MagicLinkTokenRecord
import doa.ink.workbench.data.persistence.AuthEventsTable
import doa.ink.workbench.data.persistence.AuthLoginStatesTable
import doa.ink.workbench.data.persistence.AuthSessionsTable
import doa.ink.workbench.data.persistence.BearerTokensTable
import doa.ink.workbench.data.persistence.MagicLinkTokensTable
import kotlin.uuid.toJavaUuid
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.ResultRow

internal fun ResultRow.toAuthEventRecord() =
  AuthEventRecord(
    id = this[AuthEventsTable.id].toJavaUuid(),
    authEventId = PublicId(this[AuthEventsTable.authEventId]),
    tenantId = this[AuthEventsTable.tenantId]?.toJavaUuid(),
    userId = this[AuthEventsTable.userId]?.toJavaUuid(),
    loginAccountId = this[AuthEventsTable.loginAccountId]?.toJavaUuid(),
    loginMethodId = this[AuthEventsTable.loginMethodId]?.toJavaUuid(),
    eventType = authEventTypeOf(this[AuthEventsTable.eventType]),
    result = auditEventResultOf(this[AuthEventsTable.result]),
    failureReason = this[AuthEventsTable.failureReason],
    ipAddress = this[AuthEventsTable.ipAddress],
    userAgent = this[AuthEventsTable.userAgent],
    metadata = this[AuthEventsTable.metadata],
    occurredAt = this[AuthEventsTable.occurredAt],
  )

internal fun ResultRow.toAuthSessionRecord() =
  AuthSessionRecord(
    id = this[AuthSessionsTable.id].toJavaUuid(),
    sessionHash = this[AuthSessionsTable.sessionHash],
    userId = this[AuthSessionsTable.userId].toJavaUuid(),
    loginAccountId = this[AuthSessionsTable.loginAccountId].toJavaUuid(),
    activeTenantId = this[AuthSessionsTable.activeTenantId]?.toJavaUuid(),
    expiresAt = this[AuthSessionsTable.expiresAt],
    revokedAt = this[AuthSessionsTable.revokedAt],
    lastUsedAt = this[AuthSessionsTable.lastUsedAt],
    createdAt = this[AuthSessionsTable.createdAt],
    updatedAt = this[AuthSessionsTable.updatedAt],
  )

internal fun ResultRow.toBearerTokenRecord() =
  BearerTokenRecord(
    id = this[BearerTokensTable.id].toJavaUuid(),
    tokenHash = this[BearerTokensTable.tokenHash],
    userId = this[BearerTokensTable.userId].toJavaUuid(),
    loginAccountId = this[BearerTokensTable.loginAccountId].toJavaUuid(),
    tenantId = this[BearerTokensTable.tenantId]?.toJavaUuid(),
    name = this[BearerTokensTable.name],
    scopes =
      (this[BearerTokensTable.scopes] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.content }
        ?.toSet() ?: emptySet(),
    createdBy = this[BearerTokensTable.createdBy]?.toJavaUuid(),
    expiresAt = this[BearerTokensTable.expiresAt],
    revokedAt = this[BearerTokensTable.revokedAt],
    lastUsedAt = this[BearerTokensTable.lastUsedAt],
    createdAt = this[BearerTokensTable.createdAt],
    updatedAt = this[BearerTokensTable.updatedAt],
  )

internal fun ResultRow.toAuthLoginStateRecord() =
  AuthLoginStateRecord(
    id = this[AuthLoginStatesTable.id].toJavaUuid(),
    stateHash = this[AuthLoginStatesTable.stateHash],
    tenantId = this[AuthLoginStatesTable.tenantId].toJavaUuid(),
    loginMethodId = this[AuthLoginStatesTable.loginMethodId].toJavaUuid(),
    redirectUri = this[AuthLoginStatesTable.redirectUri],
    pkceVerifier = this[AuthLoginStatesTable.pkceVerifier],
    returnUrl = this[AuthLoginStatesTable.returnUrl],
    expiresAt = this[AuthLoginStatesTable.expiresAt],
    consumedAt = this[AuthLoginStatesTable.consumedAt],
    createdAt = this[AuthLoginStatesTable.createdAt],
  )

internal fun ResultRow.toMagicLinkTokenRecord() =
  MagicLinkTokenRecord(
    id = this[MagicLinkTokensTable.id].toJavaUuid(),
    tokenHash = this[MagicLinkTokensTable.tokenHash],
    loginMethodId = this[MagicLinkTokensTable.loginMethodId].toJavaUuid(),
    tenantId = this[MagicLinkTokensTable.tenantId].toJavaUuid(),
    normalizedSubject = this[MagicLinkTokensTable.normalizedSubject],
    expiresAt = this[MagicLinkTokensTable.expiresAt],
    consumedAt = this[MagicLinkTokensTable.consumedAt],
    createdAt = this[MagicLinkTokensTable.createdAt],
  )
