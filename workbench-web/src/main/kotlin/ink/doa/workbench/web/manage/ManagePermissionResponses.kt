package ink.doa.workbench.web.manage

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import ink.doa.workbench.agile.project.ProjectSummary
import ink.doa.workbench.application.permission.GroupMemberView
import ink.doa.workbench.application.permission.PermissionBindingView
import ink.doa.workbench.application.permission.PermissionGroupView
import ink.doa.workbench.application.permission.PermissionPolicyRuleView
import ink.doa.workbench.application.permission.PermissionPolicySummary
import ink.doa.workbench.application.permission.PermissionPolicyView
import ink.doa.workbench.identity.common.summary.UserSummary
import ink.doa.workbench.identity.permission.PermissionPrincipalType
import jakarta.validation.constraints.NotBlank
import java.time.OffsetDateTime

data class CreatePermissionGroupRequest(
  @field:NotBlank val code: String,
  @field:NotBlank val name: String,
  val description: String?,
)

data class UpdatePermissionGroupRequest(
  val name: String?,
  val description: String?,
)

data class CreatePermissionPolicyRequest(
  val schemaVersion: Int = 1,
  @field:NotBlank val code: String,
  @field:NotBlank val name: String,
  val description: String?,
  val rules: List<PermissionPolicyRuleRequest> = emptyList(),
)

data class UpdatePermissionPolicyRequest(
  val name: String?,
  val description: String?,
)

data class CreatePermissionPolicyRuleRequest(
  @field:NotBlank val action: String,
  @field:NotBlank val resourcePattern: String,
  val effect: String? = null,
  val condition: Map<String, Any>? = null,
)

data class PermissionPolicyRuleRequest(
  val id: String? = null,
  @field:NotBlank val action: String,
  @field:NotBlank val resourcePattern: String,
  val effect: String = "ALLOW",
  val condition: Map<String, Any>? = null,
)

data class ReplacePermissionPolicyRequest(
  val schemaVersion: Int = 1,
  @field:NotBlank val revision: String,
  @field:NotBlank val code: String,
  @field:NotBlank val name: String,
  val description: String?,
  val rules: List<PermissionPolicyRuleRequest>,
)

data class AddGroupMemberRequest(@field:NotBlank val userId: String)

data class CreatePermissionBindingRequest(
  val principalType: PermissionPrincipalType,
  val userId: String?,
  val groupId: String?,
  @field:NotBlank val policyId: String,
  val projectId: String?,
  val validTo: OffsetDateTime? = null,
)

data class PermissionGroupResponse(
  val id: String,
  val code: String,
  val name: String,
  val description: String?,
  val builtin: Boolean,
) {
  companion object {
    fun from(view: PermissionGroupView) =
      PermissionGroupResponse(
        id = view.id,
        code = view.code,
        name = view.name,
        description = view.description,
        builtin = view.builtin,
      )
  }
}

data class GroupMemberResponse(val id: String, val user: UserSummary) {
  companion object {
    fun from(view: GroupMemberView) = GroupMemberResponse(id = view.id, user = view.user)
  }
}

data class PermissionPolicyResponse(
  val id: String,
  val schemaVersion: Int = 1,
  val code: String,
  val name: String,
  val description: String?,
  val builtin: Boolean,
  val revision: String,
  val rules: List<PermissionPolicyRuleResponse>,
) {
  companion object {
    fun from(view: PermissionPolicyView) =
      PermissionPolicyResponse(
        id = view.id,
        code = view.code,
        name = view.name,
        description = view.description,
        builtin = view.builtin,
        revision = view.revision,
        rules = view.rules.map { PermissionPolicyRuleResponse.from(it) },
      )
  }
}

data class PermissionPolicyRuleResponse(
  val id: String,
  val action: String,
  val resourcePattern: String,
  val effect: String,
  val condition: Map<String, Any>? = null,
  val position: Int,
) {
  companion object {
    private val objectMapper = ObjectMapper()

    fun from(view: PermissionPolicyRuleView) =
      PermissionPolicyRuleResponse(
        id = view.id,
        action = view.action,
        resourcePattern = view.resourcePattern,
        effect = view.effect,
        condition =
          view.condition?.let {
            objectMapper.readValue(it, object : TypeReference<Map<String, Any>>() {})
          },
        position = view.position,
      )
  }
}

data class PermissionPolicySummaryResponse(
  val id: String,
  val code: String,
  val name: String,
) {
  companion object {
    fun from(view: PermissionPolicySummary) =
      PermissionPolicySummaryResponse(id = view.id, code = view.code, name = view.name)
  }
}

data class PermissionBindingResponse(
  val id: String,
  val principalType: String,
  val user: UserSummary?,
  val group: PermissionGroupResponse?,
  val policy: PermissionPolicySummaryResponse,
  val project: ProjectSummary?,
  val validFrom: OffsetDateTime? = null,
  val validTo: OffsetDateTime? = null,
) {
  companion object {
    fun from(view: PermissionBindingView) =
      PermissionBindingResponse(
        id = view.id,
        principalType = view.principalType,
        user = view.user,
        group = view.group?.let { PermissionGroupResponse.from(it) },
        policy = PermissionPolicySummaryResponse.from(view.policy),
        project = view.project,
        validFrom = view.validFrom,
        validTo = view.validTo,
      )
  }
}
