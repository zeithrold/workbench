package one.ztd.workbench.application.permission

import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import one.ztd.workbench.identity.TenantMemberRepository
import one.ztd.workbench.identity.model.CredentialType
import one.ztd.workbench.identity.model.TenantMemberStatus
import one.ztd.workbench.identity.permission.AccessGrantRecord
import one.ztd.workbench.identity.permission.AccessGrantRepository
import one.ztd.workbench.identity.permission.AdminUserQueryRepository
import one.ztd.workbench.identity.permission.GrantScope
import one.ztd.workbench.identity.permission.PermissionBindingRepository
import one.ztd.workbench.identity.permission.PermissionConditionContext
import one.ztd.workbench.identity.permission.PermissionConditionResult
import one.ztd.workbench.identity.permission.ResolvedPermissionRule
import one.ztd.workbench.identity.permission.model.AuthorizationDecision
import one.ztd.workbench.identity.permission.model.AuthorizationRequest
import one.ztd.workbench.identity.permission.model.AuthorizationScope
import one.ztd.workbench.identity.permission.model.DecisionReason
import one.ztd.workbench.identity.permission.model.PermissionEffect
import one.ztd.workbench.identity.permission.model.PermissionService
import org.springframework.stereotype.Service

@Service
class ScopePermissionService(
  private val adminUserQueries: AdminUserQueryRepository,
  private val accessGrants: AccessGrantRepository,
  private val permissionBindings: PermissionBindingRepository,
  private val tenantMembers: TenantMemberRepository,
  private val clock: Clock,
  private val conditionEvaluator: PermissionConditionEvaluator = PermissionConditionEvaluator(),
) : PermissionService {
  override suspend fun decide(request: AuthorizationRequest): AuthorizationDecision {
    val at = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
    return when (request.scope) {
      AuthorizationScope.INSTANCE -> decideInstance(request, at)
      AuthorizationScope.TENANT -> decideTenant(request, at)
    }
  }

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

  private fun decideFromGrants(
    grants: List<AccessGrantRecord>,
    request: AuthorizationRequest,
  ): AuthorizationDecision {
    val matching = grants.filter { it.matches(request) }
    return when {
      matching.any { it.effect == PermissionEffect.DENY } -> {
        val grant = matching.first { it.effect == PermissionEffect.DENY }
        AuthorizationDecision.Deny(
          DecisionReason(
            code = "grant_denied",
            message = "A matching deny grant denied the request.",
            grantId = grant.id,
          )
        )
      }
      matching.any { it.effect == PermissionEffect.ALLOW } -> {
        val grant = matching.first { it.effect == PermissionEffect.ALLOW }
        AuthorizationDecision.Allow(
          DecisionReason(
            code = "grant_allowed",
            message = "A matching allow grant allowed the request.",
            grantId = grant.id,
          )
        )
      }
      else -> deny("no_matching_grant", "No active grant allows this request.")
    }
  }

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

  private fun decideFromRules(
    rules: List<ResolvedPermissionRule>,
    request: AuthorizationRequest,
  ): AuthorizationDecision {
    val evaluated = rules.mapNotNull { it.evaluateMatch(request) }
    evaluated
      .firstOrNull { it.contributesDeny() }
      ?.let { match ->
        return AuthorizationDecision.Deny(
          DecisionReason(
            code =
              if (match.conditionResult == PermissionConditionResult.INVALID) {
                "binding_condition_invalid"
              } else {
                "binding_denied"
              },
            message =
              if (match.conditionResult == PermissionConditionResult.INVALID) {
                "A matching policy binding has an invalid condition and denied the request."
              } else {
                "A matching policy binding denied the request."
              },
            grantId = match.rule.bindingId,
          )
        )
      }
    evaluated
      .firstOrNull { it.contributesAllow() }
      ?.let { match ->
        return AuthorizationDecision.Allow(
          DecisionReason(
            code = "binding_allowed",
            message = "A matching policy binding allowed the request.",
            grantId = match.rule.bindingId,
          )
        )
      }
    return deny("no_matching_binding", "No active policy binding allows this request.")
  }

  private fun ResolvedPermissionRule.evaluateMatch(request: AuthorizationRequest): RuleMatch? {
    if (!matchesActionAndResource(request)) return null
    val conditionResult =
      conditionEvaluator.evaluate(
        conditionJson = conditionJson,
        context =
          PermissionConditionContext(
            actorUserApiId = request.subject.userApiId,
            resourceAttributes = request.resource.attributes,
          ),
      )
    return RuleMatch(rule = this, conditionResult = conditionResult)
  }

  private fun ResolvedPermissionRule.matchesActionAndResource(
    request: AuthorizationRequest
  ): Boolean =
    action.code == request.action.code &&
      resourceMatches(resourcePattern, request.resource.canonical)

  private fun resourceMatches(pattern: String, resource: String): Boolean =
    pattern == "*" ||
      pattern == resource ||
      (pattern.endsWith(":*") && resource.startsWith(pattern.removeSuffix("*")))

  private fun deny(code: String, message: String): AuthorizationDecision.Deny =
    AuthorizationDecision.Deny(DecisionReason(code, message))

  private data class RuleMatch(
    val rule: ResolvedPermissionRule,
    val conditionResult: PermissionConditionResult,
  ) {
    fun contributesAllow(): Boolean =
      rule.effect == PermissionEffect.ALLOW && conditionResult == PermissionConditionResult.MATCH

    fun contributesDeny(): Boolean =
      rule.effect == PermissionEffect.DENY &&
        conditionResult in setOf(PermissionConditionResult.MATCH, PermissionConditionResult.INVALID)
  }
}
