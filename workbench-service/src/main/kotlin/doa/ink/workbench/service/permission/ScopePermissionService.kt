package doa.ink.workbench.service.permission

import doa.ink.workbench.core.identity.TenantMemberRepository
import doa.ink.workbench.core.identity.model.CredentialType
import doa.ink.workbench.core.identity.model.TenantMemberStatus
import doa.ink.workbench.core.permission.AccessGrantRecord
import doa.ink.workbench.core.permission.AccessGrantRepository
import doa.ink.workbench.core.permission.AdminUserQueryRepository
import doa.ink.workbench.core.permission.GrantScope
import doa.ink.workbench.core.permission.PermissionBindingRepository
import doa.ink.workbench.core.permission.ResolvedPermissionRule
import doa.ink.workbench.core.permission.model.AuthorizationDecision
import doa.ink.workbench.core.permission.model.AuthorizationRequest
import doa.ink.workbench.core.permission.model.AuthorizationScope
import doa.ink.workbench.core.permission.model.DecisionReason
import doa.ink.workbench.core.permission.model.PermissionEffect
import doa.ink.workbench.core.permission.model.PermissionService
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service

@Service
class ScopePermissionService(
  private val adminUserQueries: AdminUserQueryRepository,
  private val accessGrants: AccessGrantRepository,
  private val permissionBindings: PermissionBindingRepository,
  private val tenantMembers: TenantMemberRepository,
  private val clock: Clock,
) : PermissionService {
  @Suppress("ReturnCount")
  override suspend fun decide(request: AuthorizationRequest): AuthorizationDecision {
    val at = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
    return when (request.scope) {
      AuthorizationScope.INSTANCE -> decideInstance(request, at)
      AuthorizationScope.TENANT -> decideTenant(request, at)
    }
  }

  @Suppress("ReturnCount")
  private suspend fun decideInstance(
    request: AuthorizationRequest,
    at: OffsetDateTime,
  ): AuthorizationDecision {
    if (!adminUserQueries.isActiveInstanceAdmin(request.subject.userId, at)) {
      return deny("missing_instance_admin", "Instance administrator access is required.")
    }
    if (!credentialAllows(request)) {
      return deny("token_scope_denied", "The credential does not allow this action.")
    }
    val grants =
      accessGrants.listActiveForSubject(
        subjectUserId = request.subject.userId,
        scope = GrantScope.INSTANCE,
        tenantId = null,
        projectId = null,
        at = at,
      )
    return decideFromGrants(grants, request)
  }

  @Suppress("ReturnCount")
  private suspend fun decideTenant(
    request: AuthorizationRequest,
    at: OffsetDateTime,
  ): AuthorizationDecision {
    val tenantId =
      request.tenantId
        ?: return deny("missing_tenant", "Tenant context is required for authorization.")
    val membership =
      tenantMembers.findByTenantAndUser(tenantId, request.subject.userId)
        ?: return deny("missing_membership", "The subject is not a member of this tenant.")
    if (membership.status != TenantMemberStatus.ACTIVE) {
      return deny("inactive_membership", "The subject is not an active member of this tenant.")
    }
    if (!credentialAllows(request)) {
      return deny("token_scope_denied", "The credential does not allow this action.")
    }

    val rules =
      permissionBindings.listActiveRulesForSubject(
        subjectUserId = request.subject.userId,
        tenantId = tenantId,
        projectId = request.resource.projectId,
        at = at,
      )
    return decideFromRules(rules, request)
  }

  @Suppress("ReturnCount")
  private fun decideFromGrants(
    grants: List<AccessGrantRecord>,
    request: AuthorizationRequest,
  ): AuthorizationDecision {
    val matching = grants.filter { it.matches(request) }
    matching
      .firstOrNull { it.effect == PermissionEffect.DENY }
      ?.let {
        return AuthorizationDecision.Deny(
          DecisionReason(
            code = "grant_denied",
            message = "A matching deny grant denied the request.",
            grantId = it.id,
          )
        )
      }
    matching
      .firstOrNull { it.effect == PermissionEffect.ALLOW }
      ?.let {
        return AuthorizationDecision.Allow(
          DecisionReason(
            code = "grant_allowed",
            message = "A matching allow grant allowed the request.",
            grantId = it.id,
          )
        )
      }
    return deny("no_matching_grant", "No active grant allows this request.")
  }

  @Suppress("ReturnCount")
  private fun credentialAllows(request: AuthorizationRequest): Boolean {
    if (request.subject.credentialType == CredentialType.SESSION) return true
    val tenantId = request.tenantId ?: return false
    if (request.subject.credentialTenantId != tenantId) return false
    val scopes = request.subject.credentialScopes
    if ("workbench.api" in scopes || request.action.code in scopes) return true
    return request.action.code
      .substringBeforeLast('.', missingDelimiterValue = request.action.code)
      .let { "$it.*" in scopes }
  }

  private fun AccessGrantRecord.matches(request: AuthorizationRequest): Boolean =
    action.code == request.action.code &&
      resourceMatches(resourcePattern, request.resource.canonical)

  @Suppress("ReturnCount")
  private fun decideFromRules(
    rules: List<ResolvedPermissionRule>,
    request: AuthorizationRequest,
  ): AuthorizationDecision {
    val matching = rules.filter { it.matches(request) }
    matching
      .firstOrNull { it.effect == PermissionEffect.DENY }
      ?.let {
        return AuthorizationDecision.Deny(
          DecisionReason(
            code = "binding_denied",
            message = "A matching policy binding denied the request.",
            grantId = it.bindingId,
          )
        )
      }
    matching
      .firstOrNull { it.effect == PermissionEffect.ALLOW }
      ?.let {
        return AuthorizationDecision.Allow(
          DecisionReason(
            code = "binding_allowed",
            message = "A matching policy binding allowed the request.",
            grantId = it.bindingId,
          )
        )
      }
    return deny("no_matching_binding", "No active policy binding allows this request.")
  }

  private fun ResolvedPermissionRule.matches(request: AuthorizationRequest): Boolean =
    action.code == request.action.code &&
      resourceMatches(resourcePattern, request.resource.canonical)

  private fun resourceMatches(pattern: String, resource: String): Boolean =
    pattern == "*" ||
      pattern == resource ||
      (pattern.endsWith(":*") && resource.startsWith(pattern.removeSuffix("*")))

  private fun deny(code: String, message: String): AuthorizationDecision.Deny =
    AuthorizationDecision.Deny(DecisionReason(code, message))
}
