package one.ztd.workbench.application.project

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

@Service
class ProjectCapabilityService(
  private val permissions: PermissionService,
  private val clock: Clock,
) {
  suspend fun capabilities(
    principal: AuthenticatedPrincipal,
    tenantId: UUID,
  ): List<String> {
    val decision =
      permissions.decide(
        AuthorizationRequest(
          scope = AuthorizationScope.TENANT,
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
          action = AuthorizationAction(PROJECT_CREATE),
          resource = AuthorizationResource(type = "project", tenantId = tenantId),
          environment = AuthorizationEnvironment("project-capabilities", clock.instant()),
        )
      )
    return listOfNotNull(PROJECT_CREATE.takeIf { decision is AuthorizationDecision.Allow })
  }

  private companion object {
    const val PROJECT_CREATE = "project.create"
  }
}
