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

enum class ManagementNavigationItem {
  MANAGEMENT_INSTANCE_OVERVIEW,
  MANAGEMENT_INSTANCE_TENANTS,
  MANAGEMENT_INSTANCE_ADMINISTRATORS,
  MANAGEMENT_INSTANCE_OPERATIONS,
  MANAGEMENT_INSTANCE_OUTBOX,
  MANAGEMENT_TENANT_SETTINGS,
  MANAGEMENT_TENANT_MEMBERS,
  MANAGEMENT_TENANT_ADMINISTRATORS,
  MANAGEMENT_TENANT_ACCESS,
}

@Service
class ManagementNavigationService(
  private val permissions: PermissionService,
  private val clock: Clock,
) {
  suspend fun items(
    principal: AuthenticatedPrincipal,
    activeTenantId: UUID?,
    requestId: String?,
  ): List<ManagementNavigationItem> {
    val candidates =
      INSTANCE_CANDIDATES + if (activeTenantId == null) emptyList() else TENANT_CANDIDATES
    val checks = candidates.map { it.check }.distinct()
    val requests = checks.map { it.toRequest(principal, activeTenantId, requestId) }
    val allowedChecks =
      checks
        .zip(permissions.decideAll(requests))
        .filter { (_, decision) -> decision is AuthorizationDecision.Allow }
        .mapTo(mutableSetOf()) { (check) -> check }
    return candidates.filter { it.check in allowedChecks }.map { it.item }
  }

  private fun NavigationCheck.toRequest(
    principal: AuthenticatedPrincipal,
    activeTenantId: UUID?,
    requestId: String?,
  ): AuthorizationRequest {
    val tenantId = activeTenantId.takeIf { scope == AuthorizationScope.TENANT }
    return AuthorizationRequest(
      scope = scope,
      subject = AuthorizationSubject.from(principal, tenantId),
      tenantId = tenantId,
      action = AuthorizationAction(action),
      resource = AuthorizationResource(type = resource, tenantId = tenantId),
      environment = AuthorizationEnvironment(requestId, clock.instant()),
    )
  }

  private data class NavigationCandidate(
    val item: ManagementNavigationItem,
    val check: NavigationCheck,
  )

  private data class NavigationCheck(
    val scope: AuthorizationScope,
    val action: String,
    val resource: String,
  )

  companion object {
    private val INSTANCE_CANDIDATES =
      listOf(
        instance(
          ManagementNavigationItem.MANAGEMENT_INSTANCE_OVERVIEW,
          "operations.read",
          "operations",
        ),
        instance(
          ManagementNavigationItem.MANAGEMENT_INSTANCE_TENANTS,
          "tenant.read",
          "tenant",
        ),
        instance(
          ManagementNavigationItem.MANAGEMENT_INSTANCE_ADMINISTRATORS,
          "instance.read",
          "instance",
        ),
        instance(
          ManagementNavigationItem.MANAGEMENT_INSTANCE_OPERATIONS,
          "operations.read",
          "operations",
        ),
        instance(
          ManagementNavigationItem.MANAGEMENT_INSTANCE_OUTBOX,
          "outbox.read",
          "outbox",
        ),
      )

    private val TENANT_CANDIDATES =
      listOf(
        tenant(ManagementNavigationItem.MANAGEMENT_TENANT_SETTINGS),
        tenant(ManagementNavigationItem.MANAGEMENT_TENANT_MEMBERS),
        tenant(ManagementNavigationItem.MANAGEMENT_TENANT_ADMINISTRATORS),
        tenant(ManagementNavigationItem.MANAGEMENT_TENANT_ACCESS),
      )

    private fun instance(
      item: ManagementNavigationItem,
      action: String,
      resource: String,
    ) = NavigationCandidate(item, NavigationCheck(AuthorizationScope.INSTANCE, action, resource))

    private fun tenant(item: ManagementNavigationItem) =
      NavigationCandidate(
        item,
        NavigationCheck(AuthorizationScope.TENANT, "tenant.read", "tenant"),
      )
  }
}
