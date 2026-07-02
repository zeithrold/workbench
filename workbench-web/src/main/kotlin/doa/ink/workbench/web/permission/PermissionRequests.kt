package doa.ink.workbench.web.permission

import doa.ink.workbench.core.permission.RoleScope
import doa.ink.workbench.core.permission.model.PermissionEffect
import doa.ink.workbench.service.permission.ActionView
import doa.ink.workbench.service.permission.PermissionConditionInput
import doa.ink.workbench.service.permission.PolicyView
import doa.ink.workbench.service.permission.RoleAssignmentView
import doa.ink.workbench.service.permission.RoleView
import jakarta.validation.constraints.NotBlank

data class CreateRoleRequest(
  val scope: RoleScope = RoleScope.TENANT,
  @field:NotBlank val code: String,
  @field:NotBlank val name: String,
  val description: String? = null,
)

data class EnsureActionRequest(@field:NotBlank val code: String, val description: String? = null)

data class CreatePolicyRequest(
  @field:NotBlank val roleId: String,
  @field:NotBlank val action: String,
  val effect: PermissionEffect = PermissionEffect.ALLOW,
  @field:NotBlank val resourcePattern: String,
  val condition: PermissionConditionRequest? = null,
)

data class AssignRoleRequest(
  @field:NotBlank val userId: String,
  @field:NotBlank val roleId: String,
  val projectId: String? = null,
)

data class PermissionConditionRequest(
  val field: String? = null,
  val expected: String? = null,
  val allOf: List<PermissionConditionRequest> = emptyList(),
) {
  fun toInput(): PermissionConditionInput =
    PermissionConditionInput(
      field = field,
      expected = expected,
      allOf = allOf.map { it.toInput() },
    )
}

data class RoleResponse(
  val id: String,
  val scope: RoleScope,
  val code: String,
  val name: String,
  val description: String?,
  val builtin: Boolean,
) {
  companion object {
    fun from(view: RoleView): RoleResponse =
      RoleResponse(
        id = view.id,
        scope = view.scope,
        code = view.code,
        name = view.name,
        description = view.description,
        builtin = view.builtin,
      )
  }
}

data class ActionResponse(val code: String, val description: String?) {
  companion object {
    fun from(view: ActionView): ActionResponse =
      ActionResponse(code = view.code, description = view.description)
  }
}

data class PolicyResponse(
  val id: String,
  val roleId: String,
  val action: String,
  val effect: PermissionEffect,
  val resourcePattern: String,
  val validFrom: java.time.OffsetDateTime,
  val validTo: java.time.OffsetDateTime?,
) {
  companion object {
    fun from(view: PolicyView): PolicyResponse =
      PolicyResponse(
        id = view.id,
        roleId = view.roleId,
        action = view.action,
        effect = view.effect,
        resourcePattern = view.resourcePattern,
        validFrom = view.validFrom,
        validTo = view.validTo,
      )
  }
}

data class RoleAssignmentResponse(
  val id: String,
  val userId: String,
  val roleId: String,
  val projectId: String?,
  val validFrom: java.time.OffsetDateTime,
  val validTo: java.time.OffsetDateTime?,
) {
  companion object {
    fun from(view: RoleAssignmentView): RoleAssignmentResponse =
      RoleAssignmentResponse(
        id = view.id,
        userId = view.userId,
        roleId = view.roleId,
        projectId = view.projectId,
        validFrom = view.validFrom,
        validTo = view.validTo,
      )
  }
}
