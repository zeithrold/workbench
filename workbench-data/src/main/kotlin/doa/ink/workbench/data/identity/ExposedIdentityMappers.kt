package doa.ink.workbench.data.identity

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.identity.model.InvitationRecord
import doa.ink.workbench.core.identity.model.LoginAccountParameterKey
import doa.ink.workbench.core.identity.model.LoginAccountParameterRecord
import doa.ink.workbench.core.identity.model.LoginAccountRecord
import doa.ink.workbench.core.identity.model.LoginMethodDefinitionRecord
import doa.ink.workbench.core.identity.model.TenantLoginMethodSettingRecord
import doa.ink.workbench.core.identity.model.TenantMemberRecord
import doa.ink.workbench.core.identity.model.TenantRecord
import doa.ink.workbench.core.identity.model.UserLoginAccountRecord
import doa.ink.workbench.core.identity.model.UserRecord
import doa.ink.workbench.data.persistence.InvitationsTable
import doa.ink.workbench.data.persistence.LoginAccountParametersTable
import doa.ink.workbench.data.persistence.LoginAccountsTable
import doa.ink.workbench.data.persistence.LoginMethodDefinitionsTable
import doa.ink.workbench.data.persistence.TenantLoginMethodSettingsTable
import doa.ink.workbench.data.persistence.TenantMembersTable
import doa.ink.workbench.data.persistence.TenantsTable
import doa.ink.workbench.data.persistence.UserLoginAccountsTable
import doa.ink.workbench.data.persistence.UsersTable
import kotlin.uuid.toJavaUuid
import org.jetbrains.exposed.v1.core.ResultRow

internal fun ResultRow.toUserRecord() =
  UserRecord(
    id = this[UsersTable.id].toJavaUuid(),
    apiId = PublicId(this[UsersTable.apiId]),
    displayName = this[UsersTable.displayName],
    primaryEmail = this[UsersTable.primaryEmail],
    avatarUrl = this[UsersTable.avatarUrl],
    timezone = this[UsersTable.timezone],
    locale = this[UsersTable.locale],
    createdAt = this[UsersTable.createdAt],
    updatedAt = this[UsersTable.updatedAt],
  )

internal fun ResultRow.toTenantMemberRecord() =
  TenantMemberRecord(
    id = this[TenantMembersTable.id].toJavaUuid(),
    apiId = PublicId(this[TenantMembersTable.apiId]),
    tenantId = this[TenantMembersTable.tenantId].toJavaUuid(),
    userId = this[TenantMembersTable.userId].toJavaUuid(),
    status = tenantMemberStatusOf(this[TenantMembersTable.status]),
    joinedAt = this[TenantMembersTable.joinedAt],
    invitedBy = this[TenantMembersTable.invitedBy]?.toJavaUuid(),
    createdAt = this[TenantMembersTable.createdAt],
    updatedAt = this[TenantMembersTable.updatedAt],
  )

internal fun ResultRow.toTenantRecord() =
  TenantRecord(
    id = this[TenantsTable.id].toJavaUuid(),
    apiId = PublicId(this[TenantsTable.apiId]),
    slug = this[TenantsTable.slug],
    name = this[TenantsTable.name],
    timezone = this[TenantsTable.timezone],
    locale = this[TenantsTable.locale],
    status = tenantStatusOf(this[TenantsTable.status]),
    createdAt = this[TenantsTable.createdAt],
    updatedAt = this[TenantsTable.updatedAt],
  )

internal fun ResultRow.toInvitationRecord() =
  InvitationRecord(
    id = this[InvitationsTable.id].toJavaUuid(),
    apiId = PublicId(this[InvitationsTable.apiId]),
    type = invitationTypeOf(this[InvitationsTable.invitationType]),
    tenantId = this[InvitationsTable.tenantId].toJavaUuid(),
    email = this[InvitationsTable.email],
    normalizedEmail = this[InvitationsTable.normalizedEmail],
    displayName = this[InvitationsTable.displayName],
    tokenHash = this[InvitationsTable.tokenHash],
    invitedBy = this[InvitationsTable.invitedBy].toJavaUuid(),
    expiresAt = this[InvitationsTable.expiresAt],
    consumedAt = this[InvitationsTable.consumedAt],
    createdAt = this[InvitationsTable.createdAt],
  )

internal fun ResultRow.toLoginMethodDefinitionRecord() =
  LoginMethodDefinitionRecord(
    id = this[LoginMethodDefinitionsTable.id].toJavaUuid(),
    apiId = PublicId(this[LoginMethodDefinitionsTable.apiId]),
    code = this[LoginMethodDefinitionsTable.code],
    kind = loginMethodKindOf(this[LoginMethodDefinitionsTable.kind]),
    name = this[LoginMethodDefinitionsTable.name],
    isBuiltin = this[LoginMethodDefinitionsTable.isBuiltin],
    isEnabledGlobally = this[LoginMethodDefinitionsTable.isEnabledGlobally],
    configSchema = this[LoginMethodDefinitionsTable.configSchema],
    createdAt = this[LoginMethodDefinitionsTable.createdAt],
    updatedAt = this[LoginMethodDefinitionsTable.updatedAt],
  )

internal fun ResultRow.toTenantLoginMethodSettingRecord() =
  TenantLoginMethodSettingRecord(
    id = this[TenantLoginMethodSettingsTable.id].toJavaUuid(),
    tenantId = this[TenantLoginMethodSettingsTable.tenantId].toJavaUuid(),
    loginMethodId = this[TenantLoginMethodSettingsTable.loginMethodId].toJavaUuid(),
    isEnabled = this[TenantLoginMethodSettingsTable.isEnabled],
    allowSignup = this[TenantLoginMethodSettingsTable.allowSignup],
    displayOrder = this[TenantLoginMethodSettingsTable.displayOrder],
    config = this[TenantLoginMethodSettingsTable.config],
    secretRef = this[TenantLoginMethodSettingsTable.secretRef],
    createdBy = this[TenantLoginMethodSettingsTable.createdBy]?.toJavaUuid(),
    updatedBy = this[TenantLoginMethodSettingsTable.updatedBy]?.toJavaUuid(),
    createdAt = this[TenantLoginMethodSettingsTable.createdAt],
    updatedAt = this[TenantLoginMethodSettingsTable.updatedAt],
  )

internal fun ResultRow.toLoginAccountRecord() =
  LoginAccountRecord(
    id = this[LoginAccountsTable.id].toJavaUuid(),
    apiId = PublicId(this[LoginAccountsTable.apiId]),
    loginMethodId = this[LoginAccountsTable.loginMethodId].toJavaUuid(),
    subject = this[LoginAccountsTable.subject],
    normalizedSubject = this[LoginAccountsTable.normalizedSubject],
    displayName = this[LoginAccountsTable.displayName],
    lastUsedAt = this[LoginAccountsTable.lastUsedAt],
    disabledAt = this[LoginAccountsTable.disabledAt],
    disabledBy = this[LoginAccountsTable.disabledBy]?.toJavaUuid(),
    createdAt = this[LoginAccountsTable.createdAt],
    updatedAt = this[LoginAccountsTable.updatedAt],
  )

internal fun ResultRow.toLoginAccountParameterRecord() =
  LoginAccountParameterRecord(
    id = this[LoginAccountParametersTable.id].toJavaUuid(),
    loginAccountId = this[LoginAccountParametersTable.loginAccountId].toJavaUuid(),
    parameterKey = LoginAccountParameterKey(this[LoginAccountParametersTable.parameterKey]),
    parameterValue = this[LoginAccountParametersTable.parameterValue],
    secretRef = this[LoginAccountParametersTable.secretRef],
    metadata = this[LoginAccountParametersTable.metadata],
    createdAt = this[LoginAccountParametersTable.createdAt],
    updatedAt = this[LoginAccountParametersTable.updatedAt],
  )

internal fun ResultRow.toUserLoginAccountRecord() =
  UserLoginAccountRecord(
    id = this[UserLoginAccountsTable.id].toJavaUuid(),
    userId = this[UserLoginAccountsTable.userId].toJavaUuid(),
    loginAccountId = this[UserLoginAccountsTable.loginAccountId].toJavaUuid(),
    linkedBy = this[UserLoginAccountsTable.linkedBy]?.toJavaUuid(),
    linkedAt = this[UserLoginAccountsTable.linkedAt],
    unlinkedAt = this[UserLoginAccountsTable.unlinkedAt],
  )
