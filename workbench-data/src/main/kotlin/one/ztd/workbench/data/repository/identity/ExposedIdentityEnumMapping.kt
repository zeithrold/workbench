package one.ztd.workbench.data.repository.identity

import one.ztd.workbench.identity.model.AuditEventResult
import one.ztd.workbench.identity.model.AuthEventType
import one.ztd.workbench.identity.model.InvitationType
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.identity.model.TenantMemberStatus
import one.ztd.workbench.tenant.model.TenantStatus

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
