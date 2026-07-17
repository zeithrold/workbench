package one.ztd.workbench.application.permission

import java.util.UUID
import one.ztd.workbench.identity.permission.model.PermissionEffect

data class AddPolicyRuleCommand(
  val tenantId: UUID,
  val policyPublicId: String,
  val action: String,
  val resourcePattern: String,
  val effect: PermissionEffect,
  val conditionJson: String? = null,
)
