package doa.ink.workbench.data.identity

import doa.ink.workbench.core.identity.model.AuditEventResult
import doa.ink.workbench.core.identity.model.AuthEventType
import doa.ink.workbench.core.identity.model.InvitationType
import doa.ink.workbench.core.identity.model.LoginMethodKind
import doa.ink.workbench.core.identity.model.TenantMemberStatus
import doa.ink.workbench.core.identity.model.TenantStatus

internal fun tenantMemberStatusOf(value: String): TenantMemberStatus =
  TenantMemberStatus.entries.single { it.dbValue == value }

internal fun tenantStatusOf(value: String): TenantStatus =
  TenantStatus.entries.single { it.dbValue == value }

internal fun invitationTypeOf(value: String): InvitationType =
  InvitationType.entries.single { it.dbValue == value }

internal fun loginMethodKindOf(value: String): LoginMethodKind =
  LoginMethodKind.entries.single { it.dbValue == value }

internal fun authEventTypeOf(value: String): AuthEventType =
  AuthEventType.entries.single { it.dbValue == value }

internal fun auditEventResultOf(value: String): AuditEventResult =
  AuditEventResult.entries.single { it.dbValue == value }
