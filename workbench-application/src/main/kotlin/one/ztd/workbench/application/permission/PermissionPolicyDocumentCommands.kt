package one.ztd.workbench.application.permission

import java.util.UUID
import one.ztd.workbench.identity.permission.model.PermissionEffect

data class PermissionPolicyDocumentRuleCommand(
  val id: String?,
  val action: String,
  val resourcePattern: String,
  val effect: PermissionEffect,
  val conditionJson: String?,
)

data class CreatePermissionPolicyDocumentCommand(
  val tenantId: UUID,
  val schemaVersion: Int,
  val code: String,
  val name: String,
  val description: String?,
  val rules: List<PermissionPolicyDocumentRuleCommand>,
)

data class ReplacePermissionPolicyDocumentCommand(
  val tenantId: UUID,
  val policyPublicId: String,
  val schemaVersion: Int,
  val revision: String,
  val code: String,
  val name: String,
  val description: String?,
  val rules: List<PermissionPolicyDocumentRuleCommand>,
)
