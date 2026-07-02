package doa.ink.workbench.service.permission

import doa.ink.workbench.core.identity.TenantMemberRepository
import doa.ink.workbench.core.identity.model.CredentialType
import doa.ink.workbench.core.identity.model.TenantMemberStatus
import doa.ink.workbench.core.permission.PermissionPolicyRecord
import doa.ink.workbench.core.permission.PermissionPolicyRepository
import doa.ink.workbench.core.permission.RoleAssignmentRepository
import doa.ink.workbench.core.permission.model.AuthorizationDecision
import doa.ink.workbench.core.permission.model.AuthorizationRequest
import doa.ink.workbench.core.permission.model.DecisionReason
import doa.ink.workbench.core.permission.model.PermissionCondition
import doa.ink.workbench.core.permission.model.PermissionEffect
import doa.ink.workbench.core.permission.model.PermissionService
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@Service
class DefaultPermissionService(
  private val tenantMembers: TenantMemberRepository,
  private val roleAssignments: RoleAssignmentRepository,
  private val permissionPolicies: PermissionPolicyRepository,
  private val clock: Clock,
) : PermissionService {
  @Suppress("ReturnCount")
  override suspend fun decide(request: AuthorizationRequest): AuthorizationDecision {
    val membership =
      tenantMembers.findByTenantAndUser(request.tenantId, request.subject.userId)
        ?: return deny("missing_membership", "The subject is not a member of this tenant.")
    if (membership.status != TenantMemberStatus.ACTIVE) {
      return deny("inactive_membership", "The subject is not an active member of this tenant.")
    }
    if (!credentialAllows(request)) {
      return deny("token_scope_denied", "The credential does not allow this action.")
    }

    val at = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
    val assignments =
      roleAssignments.listActiveByUser(
        tenantId = request.tenantId,
        userId = request.subject.userId,
        projectId = request.resource.projectId,
        at = at,
      )
    if (assignments.isEmpty()) {
      return deny("missing_role_assignment", "The subject has no active role assignment.")
    }

    val policies =
      permissionPolicies.listActiveByRoles(
        tenantId = request.tenantId,
        roleIds = assignments.map { it.roleId }.toSet(),
        at = at,
      )
    val matchingPolicies = policies.filter { it.matches(request) && it.condition.matches(request) }
    matchingPolicies
      .firstOrNull { it.effect == PermissionEffect.DENY }
      ?.let {
        return AuthorizationDecision.Deny(
          DecisionReason(
            code = "policy_denied",
            message = "A matching deny policy denied the request.",
            policyId = it.id,
            roleId = it.roleId,
          )
        )
      }
    matchingPolicies
      .firstOrNull { it.effect == PermissionEffect.ALLOW }
      ?.let {
        return AuthorizationDecision.Allow(
          DecisionReason(
            code = "policy_allowed",
            message = "A matching allow policy allowed the request.",
            policyId = it.id,
            roleId = it.roleId,
          )
        )
      }
    return deny("no_matching_policy", "No active policy allows this request.")
  }

  @Suppress("ReturnCount")
  private fun credentialAllows(request: AuthorizationRequest): Boolean {
    if (request.subject.credentialType == CredentialType.SESSION) return true
    if (request.subject.credentialTenantId != request.tenantId) return false
    val scopes = request.subject.credentialScopes
    if ("workbench.api" in scopes || request.action.code in scopes) return true
    return request.action.code
      .substringBeforeLast('.', missingDelimiterValue = request.action.code)
      .let { "$it.*" in scopes }
  }

  private fun PermissionPolicyRecord.matches(request: AuthorizationRequest): Boolean =
    action.code == request.action.code &&
      resourceMatches(resourcePattern, request.resource.canonical)

  private fun resourceMatches(pattern: String, resource: String): Boolean =
    pattern == "*" ||
      pattern == resource ||
      (pattern.endsWith(":*") && resource.startsWith(pattern.removeSuffix("*")))

  private fun PermissionCondition?.matches(request: AuthorizationRequest): Boolean =
    when (this) {
      null -> true
      is PermissionCondition.FieldEquals -> request.fieldValue(field) == expected
      is PermissionCondition.AllOf -> conditions.all { it.matches(request) }
    }

  private fun AuthorizationRequest.fieldValue(field: String): String? =
    when (field) {
      "tenantId" -> tenantId.toString()
      "resource.type" -> resource.type
      "resource.id" -> resource.id
      "resource.projectId" -> resource.projectId?.toString()
      else -> resource.attributes[field] ?: environment.attributes[field]
    }

  private fun deny(code: String, message: String): AuthorizationDecision.Deny =
    AuthorizationDecision.Deny(DecisionReason(code, message))
}
