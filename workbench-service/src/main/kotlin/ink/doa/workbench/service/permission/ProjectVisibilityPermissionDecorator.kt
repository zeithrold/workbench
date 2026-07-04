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
  override suspend fun decide(request: AuthorizationRequest): AuthorizationDecision {
    val decision = delegate.decide(request)
    val projectId = request.resource.projectId
    val tenantId = request.tenantId
    return when {
      decision is AuthorizationDecision.Allow -> decision
      request.scope != AuthorizationScope.TENANT -> decision
      projectId == null || tenantId == null -> decision
      decision.reason.code != "no_matching_binding" -> decision
      projectAccess.allowsVisibilityAction(
        userId = request.subject.userId,
        tenantId = tenantId,
        projectId = projectId,
        action = request.action,
      ) ->
        AuthorizationDecision.Allow(
          DecisionReason(
            code = "visibility_allowed",
            message = "Project visibility settings allowed the request.",
          )
        )
      else -> decision
    }
  }
}
