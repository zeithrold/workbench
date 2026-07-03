package ink.doa.workbench.service.permission

import ink.doa.workbench.agile.project.ProjectAccessService
import ink.doa.workbench.core.permission.model.AuthorizationDecision
import ink.doa.workbench.core.permission.model.AuthorizationRequest
import ink.doa.workbench.core.permission.model.AuthorizationScope
import ink.doa.workbench.core.permission.model.DecisionReason
import ink.doa.workbench.core.permission.model.PermissionService
import ink.doa.workbench.security.permission.ScopePermissionService
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service

@Service
@Primary
class ProjectVisibilityPermissionDecorator(
  private val delegate: ScopePermissionService,
  private val projectAccess: ProjectAccessService,
) : PermissionService {
  @Suppress("ReturnCount")
  override suspend fun decide(request: AuthorizationRequest): AuthorizationDecision {
    val decision = delegate.decide(request)
    if (decision is AuthorizationDecision.Allow) return decision
    if (request.scope != AuthorizationScope.TENANT) return decision
    val projectId = request.resource.projectId ?: return decision
    val tenantId = request.tenantId ?: return decision
    if (decision.reason.code != "no_matching_binding") return decision
    if (
      projectAccess.allowsVisibilityAction(
        userId = request.subject.userId,
        tenantId = tenantId,
        projectId = projectId,
        action = request.action,
      )
    ) {
      return AuthorizationDecision.Allow(
        DecisionReason(
          code = "visibility_allowed",
          message = "Project visibility settings allowed the request.",
        )
      )
    }
    return decision
  }
}
