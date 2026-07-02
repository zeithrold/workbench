package doa.ink.workbench.service.permission

import doa.ink.workbench.core.permission.PermissionActionRecord
import doa.ink.workbench.core.permission.PermissionPolicyRecord
import doa.ink.workbench.core.permission.RoleAssignmentRecord
import doa.ink.workbench.core.permission.RoleRecord
import doa.ink.workbench.core.permission.RoleScope
import doa.ink.workbench.core.permission.model.PermissionEffect
import java.time.OffsetDateTime

data class RoleView(
  val id: String,
  val scope: RoleScope,
  val code: String,
  val name: String,
  val description: String?,
  val builtin: Boolean,
) {
  companion object {
    fun from(record: RoleRecord): RoleView =
      RoleView(
        id = record.apiId.value,
        scope = record.scope,
        code = record.code,
        name = record.name,
        description = record.description,
        builtin = record.isBuiltin,
      )
  }
}

data class ActionView(
  val code: String,
  val description: String?,
) {
  companion object {
    fun from(record: PermissionActionRecord): ActionView =
      ActionView(code = record.code.code, description = record.description)
  }
}

data class PolicyView(
  val id: String,
  val roleId: String,
  val action: String,
  val effect: PermissionEffect,
  val resourcePattern: String,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime?,
) {
  companion object {
    fun from(record: PermissionPolicyRecord, rolePublicId: String): PolicyView =
      PolicyView(
        id = record.apiId.value,
        roleId = rolePublicId,
        action = record.action.code,
        effect = record.effect,
        resourcePattern = record.resourcePattern,
        validFrom = record.validFrom,
        validTo = record.validTo,
      )
  }
}

data class RoleAssignmentView(
  val id: String,
  val userId: String,
  val roleId: String,
  val projectId: String?,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime?,
) {
  companion object {
    fun from(
      record: RoleAssignmentRecord,
      userPublicId: String,
      rolePublicId: String,
      projectPublicId: String?,
    ): RoleAssignmentView =
      RoleAssignmentView(
        id = record.apiId.value,
        userId = userPublicId,
        roleId = rolePublicId,
        projectId = projectPublicId,
        validFrom = record.validFrom,
        validTo = record.validTo,
      )
  }
}
