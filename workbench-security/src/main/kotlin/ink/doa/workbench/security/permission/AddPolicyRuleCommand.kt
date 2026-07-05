package ink.doa.workbench.security.permission

import ink.doa.workbench.core.permission.model.PermissionEffect
import java.util.UUID

data class AddPolicyRuleCommand(
  val tenantId: UUID,
  val policyPublicId: String,
  val action: String,
  val resourcePattern: String,
  val effect: PermissionEffect,
  val conditionJson: String? = null,
)
