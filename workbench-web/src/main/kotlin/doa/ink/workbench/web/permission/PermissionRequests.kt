package doa.ink.workbench.web.permission

import doa.ink.workbench.core.permission.RoleScope
import doa.ink.workbench.core.permission.model.PermissionEffect
import doa.ink.workbench.service.permission.ActionView
import doa.ink.workbench.service.permission.PermissionConditionInput
import doa.ink.workbench.service.permission.PolicyView
import doa.ink.workbench.service.permission.RoleAssignmentView
import doa.ink.workbench.service.permission.RoleView
import doa.ink.workbench.web.api.OpenApiExamples
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "Create a tenant-scoped role.")
data class CreateRoleRequest(
  @field:Schema(description = "Role scope.", example = "TENANT")
  val scope: RoleScope = RoleScope.TENANT,
  @field:NotBlank
  @field:Schema(description = "Stable role code within the tenant.", example = "admin")
  val code: String,
  @field:NotBlank
  @field:Schema(description = "Display name.", example = "Administrator")
  val name: String,
  @field:Schema(description = "Optional description.", example = "Full access")
  val description: String? = null,
)

@Schema(description = "Ensure a global permission action exists in the registry.")
data class EnsureActionRequest(
  @field:NotBlank
  @field:Schema(description = "Action code.", example = "project.create")
  val code: String,
  @field:Schema(
    description = "Human-readable description.",
    example = "Create projects in the tenant.",
  )
  val description: String? = null,
)

@Schema(description = "Create an allow or deny policy for a role.")
data class CreatePolicyRequest(
  @field:NotBlank
  @field:Schema(description = "Public role id.", example = OpenApiExamples.ROLE_ID)
  val roleId: String,
  @field:NotBlank
  @field:Schema(description = "Permission action code.", example = "project.create")
  val action: String,
  @field:Schema(description = "Policy effect.", example = "ALLOW")
  val effect: PermissionEffect = PermissionEffect.ALLOW,
  @field:NotBlank
  @field:Schema(description = "Resource pattern the policy applies to.", example = "project")
  val resourcePattern: String,
  @field:Schema(description = "Optional attribute condition.")
  val condition: PermissionConditionRequest? = null,
)

@Schema(description = "Assign a role to a user, optionally scoped to a project.")
data class AssignRoleRequest(
  @field:NotBlank
  @field:Schema(description = "Public user id.", example = OpenApiExamples.USER_ID)
  val userId: String,
  @field:NotBlank
  @field:Schema(description = "Public role id.", example = OpenApiExamples.ROLE_ID)
  val roleId: String,
  @field:Schema(
    description = "Optional project scope. Omit for tenant-wide assignment.",
    example = OpenApiExamples.PROJECT_ID,
  )
  val projectId: String? = null,
)

@Schema(description = "Attribute-based policy condition.")
data class PermissionConditionRequest(
  @field:Schema(description = "Attribute field name.", example = "project.identifier")
  val field: String? = null,
  @field:Schema(description = "Expected attribute value.", example = "CORE")
  val expected: String? = null,
  @field:Schema(description = "Nested AND conditions.")
  val allOf: List<PermissionConditionRequest> = emptyList(),
) {
  fun toInput(): PermissionConditionInput =
    PermissionConditionInput(
      field = field,
      expected = expected,
      allOf = allOf.map { it.toInput() },
    )
}

@Schema(description = "Tenant or global role definition.")
data class RoleResponse(
  @field:Schema(description = "Public role id.", example = OpenApiExamples.ROLE_ID) val id: String,
  @field:Schema(description = "Role scope.", example = "TENANT") val scope: RoleScope,
  @field:Schema(description = "Stable role code.", example = "admin") val code: String,
  @field:Schema(description = "Display name.", example = "Administrator") val name: String,
  @field:Schema(description = "Optional description.", example = "Full access")
  val description: String?,
  @field:Schema(description = "Whether the role is built in.", example = "true")
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

@Schema(description = "Registered permission action.")
data class ActionResponse(
  @field:Schema(description = "Action code.", example = "project.create") val code: String,
  @field:Schema(
    description = "Human-readable description.",
    example = "Create projects in the tenant.",
  )
  val description: String?,
) {
  companion object {
    fun from(view: ActionView): ActionResponse =
      ActionResponse(code = view.code, description = view.description)
  }
}

@Schema(description = "Role policy granting or denying an action on a resource pattern.")
data class PolicyResponse(
  @field:Schema(description = "Public policy id.", example = OpenApiExamples.POLICY_ID)
  val id: String,
  @field:Schema(description = "Public role id.", example = OpenApiExamples.ROLE_ID)
  val roleId: String,
  @field:Schema(description = "Permission action code.", example = "project.create")
  val action: String,
  @field:Schema(description = "Policy effect.", example = "ALLOW") val effect: PermissionEffect,
  @field:Schema(description = "Resource pattern.", example = "project") val resourcePattern: String,
  @field:Schema(
    description = "When the policy becomes effective.",
    example = "2026-07-02T10:00:00+00:00",
  )
  val validFrom: java.time.OffsetDateTime,
  @field:Schema(description = "When the policy expires, if set.")
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

@Schema(description = "Assignment of a role to a user.")
data class RoleAssignmentResponse(
  @field:Schema(description = "Public assignment id.", example = OpenApiExamples.ASSIGNMENT_ID)
  val id: String,
  @field:Schema(description = "Public user id.", example = OpenApiExamples.USER_ID)
  val userId: String,
  @field:Schema(description = "Public role id.", example = OpenApiExamples.ROLE_ID)
  val roleId: String,
  @field:Schema(description = "Optional project scope.", example = OpenApiExamples.PROJECT_ID)
  val projectId: String?,
  @field:Schema(
    description = "When the assignment becomes effective.",
    example = "2026-07-02T10:00:00+00:00",
  )
  val validFrom: java.time.OffsetDateTime,
  @field:Schema(description = "When the assignment expires, if set.")
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
