package ink.doa.workbench.application.permission

import ink.doa.workbench.identity.permission.model.PermissionEffect
import java.util.UUID

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
