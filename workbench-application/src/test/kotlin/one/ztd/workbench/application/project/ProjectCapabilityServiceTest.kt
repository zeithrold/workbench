package one.ztd.workbench.application.project

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.identity.permission.model.AuthorizationDecision
import one.ztd.workbench.identity.permission.model.AuthorizationRequest
import one.ztd.workbench.identity.permission.model.DecisionReason
import one.ztd.workbench.identity.permission.model.PermissionService
import one.ztd.workbench.kernel.common.ids.PublicId

class ProjectCapabilityServiceTest :
  StringSpec({
    val permissions = mockk<PermissionService>()
    val service =
      ProjectCapabilityService(
        permissions,
        Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC),
      )

    "returns project create only when explicitly allowed" {
      coEvery { permissions.decide(any()) } answers
        {
          val request = firstArg<AuthorizationRequest>()
          request.action.code shouldBe "project.create"
          AuthorizationDecision.Allow(DecisionReason("grant", "allowed"))
        }

      runBlocking { service.capabilities(PRINCIPAL, TENANT_ID) } shouldContainExactly
        listOf("project.create")
    }

    "does not infer project create from tenant membership" {
      coEvery { permissions.decide(any()) } returns
        AuthorizationDecision.Deny(DecisionReason("missing", "denied"))

      runBlocking { service.capabilities(PRINCIPAL, TENANT_ID) } shouldContainExactly emptyList()
    }
  }) {
  private companion object {
    val TENANT_ID: UUID = UUID.randomUUID()
    val PRINCIPAL =
      AuthenticatedPrincipal(
        user =
          UserRecord(
            id = UUID.randomUUID(),
            apiId = PublicId.new("usr"),
            displayName = "Project member",
            primaryEmail = "member@example.test",
          ),
        loginAccountId = UUID.randomUUID(),
        sessionId = "session",
        bearerTokenId = null,
        tenantId = TENANT_ID,
      )
  }
}
