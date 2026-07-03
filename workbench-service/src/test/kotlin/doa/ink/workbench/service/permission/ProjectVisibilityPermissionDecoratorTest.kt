package doa.ink.workbench.service.permission

import doa.ink.workbench.agile.project.ProjectAccessService
import doa.ink.workbench.core.identity.model.CredentialType
import doa.ink.workbench.core.permission.model.AuthorizationAction
import doa.ink.workbench.core.permission.model.AuthorizationDecision
import doa.ink.workbench.core.permission.model.AuthorizationEnvironment
import doa.ink.workbench.core.permission.model.AuthorizationRequest
import doa.ink.workbench.core.permission.model.AuthorizationResource
import doa.ink.workbench.core.permission.model.AuthorizationScope
import doa.ink.workbench.core.permission.model.AuthorizationSubject
import doa.ink.workbench.core.permission.model.DecisionReason
import doa.ink.workbench.security.permission.ScopePermissionService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.util.UUID

class ProjectVisibilityPermissionDecoratorTest :
  StringSpec({
    val delegate = mockk<ScopePermissionService>()
    val projectAccess = mockk<ProjectAccessService>()
    val decorator = ProjectVisibilityPermissionDecorator(delegate, projectAccess)
    val tenantId = UUID.randomUUID()
    val userId = UUID.randomUUID()
    val projectId = UUID.randomUUID()

    fun request(action: String) =
      AuthorizationRequest(
        scope = AuthorizationScope.TENANT,
        subject =
          AuthorizationSubject(
            userId = userId,
            loginAccountId = null,
            credentialType = CredentialType.SESSION,
            credentialId = null,
            credentialTenantId = tenantId,
            credentialScopes = emptySet(),
          ),
        tenantId = tenantId,
        action = AuthorizationAction(action),
        resource =
          AuthorizationResource(
            type = "project",
            id = "prj_test",
            tenantId = tenantId,
            projectId = projectId,
          ),
        environment = AuthorizationEnvironment(requestId = "req", occurredAt = Instant.now()),
      )

    "decorator allows read when visibility grants access after binding deny" {
      coEvery { delegate.decide(any()) } returns
        AuthorizationDecision.Deny(
          DecisionReason("no_matching_binding", "No active policy binding allows the request.")
        )
      coEvery {
        projectAccess.allowsVisibilityAction(
          userId,
          tenantId,
          projectId,
          AuthorizationAction("project.read"),
        )
      } returns true

      decorator.decide(request("project.read")).shouldBeInstanceOf<AuthorizationDecision.Allow>()
    }
  })
