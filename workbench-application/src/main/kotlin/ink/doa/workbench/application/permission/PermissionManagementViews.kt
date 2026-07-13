package ink.doa.workbench.application.permission

import ink.doa.workbench.agile.project.ProjectSummary
import ink.doa.workbench.identity.common.summary.UserSummary
import ink.doa.workbench.identity.permission.PermissionGroupRecord
import ink.doa.workbench.identity.permission.PermissionPolicyRecord
import ink.doa.workbench.identity.permission.PermissionPolicyRuleRecord

data class PermissionGroupView(
  val id: String,
  val code: String,
  val name: String,
  val description: String?,
  val builtin: Boolean,
) {
  companion object {
    fun from(record: PermissionGroupRecord) =
      PermissionGroupView(
        id = record.apiId.value,
        code = record.code,
        name = record.name,
        description = record.description,
        builtin = record.builtin,
      )
  }
}

data class GroupMemberView(val id: String, val user: UserSummary)

data class PermissionPolicySummary(
  val id: String,
  val code: String,
  val name: String,
) {
  companion object {
    fun from(record: PermissionPolicyRecord) =
      PermissionPolicySummary(id = record.apiId.value, code = record.code, name = record.name)
  }
}

data class PermissionPolicyView(
  val id: String,
  val code: String,
  val name: String,
  val description: String?,
  val builtin: Boolean,
  val rules: List<PermissionPolicyRuleView>,
) {
  companion object {
    fun from(
      record: PermissionPolicyRecord,
      rules: List<PermissionPolicyRuleRecord>,
    ) =
      PermissionPolicyView(
        id = record.apiId.value,
        code = record.code,
        name = record.name,
        description = record.description,
        builtin = record.builtin,
        rules = rules.map { PermissionPolicyRuleView.from(it) },
      )
  }
}

data class PermissionPolicyRuleView(
  val action: String,
  val resourcePattern: String,
  val effect: String,
  val condition: String? = null,
) {
  companion object {
    fun from(record: PermissionPolicyRuleRecord) =
      PermissionPolicyRuleView(
        action = record.action.code,
        resourcePattern = record.resourcePattern,
        effect = record.effect.name,
        condition = record.conditionJson,
      )
  }
}

data class PermissionBindingView(
  val id: String,
  val principalType: String,
  val user: UserSummary?,
  val group: PermissionGroupView?,
  val policy: PermissionPolicySummary,
  val project: ProjectSummary?,
)
