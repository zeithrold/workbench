package one.ztd.workbench.application.permission

import java.time.OffsetDateTime
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.identity.permission.AccessGrantRecord
import one.ztd.workbench.identity.permission.AdminScope
import one.ztd.workbench.identity.permission.GrantScope
import one.ztd.workbench.identity.permission.model.PermissionEffect

data class AdminUserView(
  val id: String,
  val userId: String,
  val scope: AdminScope,
  val tenantId: String?,
  val status: String,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime?,
) {
  companion object {
    fun from(record: one.ztd.workbench.identity.permission.AdminUserRecord, user: UserRecord) =
      AdminUserView(
        id = record.apiId.value,
        userId = user.apiId.value,
        scope = record.scope,
        tenantId = record.tenantId?.toString(),
        status = record.status.dbValue,
        validFrom = record.validFrom,
        validTo = record.validTo,
      )
  }
}

data class AccessGrantView(
  val id: String,
  val scope: GrantScope,
  val tenantId: String?,
  val projectId: String?,
  val userId: String,
  val action: String,
  val resourcePattern: String,
  val effect: PermissionEffect,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime?,
) {
  companion object {
    fun from(record: AccessGrantRecord): AccessGrantView =
      AccessGrantView(
        id = record.apiId.value,
        scope = record.scope,
        tenantId = record.tenantId?.toString(),
        projectId = record.projectId?.toString(),
        userId = record.subjectUserId.toString(),
        action = record.action.code,
        resourcePattern = record.resourcePattern,
        effect = record.effect,
        validFrom = record.validFrom,
        validTo = record.validTo,
      )
  }
}

data class ActionView(
  val code: String,
  val description: String?,
) {
  companion object {
    fun from(record: one.ztd.workbench.identity.permission.PermissionActionRecord): ActionView =
      ActionView(code = record.code.code, description = record.description)
  }
}
