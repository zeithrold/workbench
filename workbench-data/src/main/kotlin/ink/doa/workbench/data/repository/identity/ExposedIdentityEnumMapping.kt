package ink.doa.workbench.data.repository.identity

import ink.doa.workbench.identity.model.AuditEventResult
import ink.doa.workbench.identity.model.AuthEventType
import ink.doa.workbench.identity.model.InvitationType
import ink.doa.workbench.identity.model.LoginMethodKind
import ink.doa.workbench.identity.model.TenantMemberStatus
import ink.doa.workbench.tenant.model.TenantStatus

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
