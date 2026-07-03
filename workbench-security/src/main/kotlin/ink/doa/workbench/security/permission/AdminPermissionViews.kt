package ink.doa.workbench.security.permission

import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.core.permission.AccessGrantRecord
import ink.doa.workbench.core.permission.AdminScope
import ink.doa.workbench.core.permission.GrantScope
import ink.doa.workbench.core.permission.model.PermissionEffect
import java.time.OffsetDateTime

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
    fun from(record: ink.doa.workbench.core.permission.AdminUserRecord, user: UserRecord) =
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
    fun from(record: ink.doa.workbench.core.permission.PermissionActionRecord): ActionView =
      ActionView(code = record.code.code, description = record.description)
  }
}
