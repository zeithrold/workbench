package one.ztd.workbench.application.permission

import one.ztd.workbench.agile.project.ProjectAccessService
import one.ztd.workbench.identity.permission.model.AuthorizationDecision
import one.ztd.workbench.identity.permission.model.AuthorizationRequest
import one.ztd.workbench.identity.permission.model.AuthorizationScope
import one.ztd.workbench.identity.permission.model.DecisionReason
import one.ztd.workbench.identity.permission.model.PermissionService
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
    return applyProjectVisibility(request, decision)
  }

  override suspend fun decideAll(
    requests: List<AuthorizationRequest>
  ): List<AuthorizationDecision> =
    requests.zip(delegate.decideAll(requests)).map { (request, decision) ->
      applyProjectVisibility(request, decision)
    }

  private suspend fun applyProjectVisibility(
    request: AuthorizationRequest,
    decision: AuthorizationDecision,
  ): AuthorizationDecision {
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
