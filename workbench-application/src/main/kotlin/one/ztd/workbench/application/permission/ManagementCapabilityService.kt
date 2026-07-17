package one.ztd.workbench.application.permission

import java.time.Clock
import java.util.UUID
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.permission.model.AuthorizationAction
import one.ztd.workbench.identity.permission.model.AuthorizationDecision
import one.ztd.workbench.identity.permission.model.AuthorizationEnvironment
import one.ztd.workbench.identity.permission.model.AuthorizationRequest
import one.ztd.workbench.identity.permission.model.AuthorizationResource
import one.ztd.workbench.identity.permission.model.AuthorizationScope
import one.ztd.workbench.identity.permission.model.AuthorizationSubject
import one.ztd.workbench.identity.permission.model.PermissionService
import org.springframework.stereotype.Service

data class ManagementCapability(val action: String, val resource: String)

@Service
class ManagementCapabilityService(
  private val permissions: PermissionService,
  private val clock: Clock,
) {
  suspend fun instanceCapabilities(principal: AuthenticatedPrincipal): List<String> =
    allowed(principal, AuthorizationScope.INSTANCE, tenantId = null, INSTANCE_CAPABILITIES)

  suspend fun tenantCapabilities(
    principal: AuthenticatedPrincipal,
    tenantId: UUID,
  ): List<String> = allowed(principal, AuthorizationScope.TENANT, tenantId, TENANT_CAPABILITIES)

  private suspend fun allowed(
    principal: AuthenticatedPrincipal,
    scope: AuthorizationScope,
    tenantId: UUID?,
    candidates: List<ManagementCapability>,
  ): List<String> = candidates.mapNotNull { capability ->
    val decision =
      permissions.decide(
        AuthorizationRequest(
          scope = scope,
          subject =
            AuthorizationSubject(
              userId = principal.user.id,
              userApiId = principal.user.apiId.value,
              loginAccountId = principal.loginAccountId,
              credentialType = principal.credentialType,
              credentialId = principal.bearerTokenId ?: principal.sessionId,
              credentialTenantId = principal.tenantId ?: tenantId,
              credentialScopes = principal.credentialScopes,
            ),
          tenantId = tenantId,
          action = AuthorizationAction(capability.action),
          resource =
            AuthorizationResource(
              type = capability.resource.substringBefore(':'),
              tenantId = tenantId,
            ),
          environment = AuthorizationEnvironment("management-capabilities", clock.instant()),
        )
      )
    capability.action.takeIf { decision is AuthorizationDecision.Allow }
  }

  companion object {
    val INSTANCE_CAPABILITIES =
      listOf(
        ManagementCapability("instance.read", "instance"),
        ManagementCapability("instance.admin.manage", "instance-admin:*"),
        ManagementCapability("tenant.create", "tenant:*"),
        ManagementCapability("tenant.read", "tenant:*"),
        ManagementCapability("tenant.update", "tenant:*"),
        ManagementCapability("tenant.delete", "tenant:*"),
        ManagementCapability("operations.read", "operations"),
        ManagementCapability("outbox.read", "outbox"),
        ManagementCapability("outbox.manage", "outbox"),
      )

    val TENANT_CAPABILITIES =
      listOf(
        ManagementCapability("tenant.read", "tenant:*"),
        ManagementCapability("tenant.update", "tenant:*"),
        ManagementCapability("tenant.member.manage", "tenant:*"),
        ManagementCapability("permission.group.manage", "permission:*"),
        ManagementCapability("permission.policy.manage", "permission:*"),
        ManagementCapability("permission.assignment.manage", "permission:*"),
      )
  }
}
