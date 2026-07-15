package ink.doa.workbench.application.permission

import ink.doa.workbench.identity.model.AuthenticatedPrincipal
import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.identity.permission.model.AuthorizationDecision
import ink.doa.workbench.identity.permission.model.DecisionReason
import ink.doa.workbench.identity.permission.model.PermissionService
import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

class ManagementCapabilityServiceTest :
  StringSpec({
    val permissions = mockk<PermissionService>()
    val service =
      ManagementCapabilityService(
        permissions,
        Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC),
      )

    "returns only actions allowed by the permission engine" {
      coEvery { permissions.decide(any()) } answers
        {
          val action =
            firstArg<ink.doa.workbench.identity.permission.model.AuthorizationRequest>().action.code
          if (action in setOf("instance.read", "operations.read")) {
            AuthorizationDecision.Allow(DecisionReason("grant", "allowed"))
          } else {
            AuthorizationDecision.Deny(DecisionReason("missing", "denied"))
          }
        }

      runBlocking { service.instanceCapabilities(PRINCIPAL) } shouldContainExactly
        listOf("instance.read", "operations.read")
    }

    "does not infer tenant capabilities from administrator identity" {
      coEvery { permissions.decide(any()) } returns
        AuthorizationDecision.Deny(DecisionReason("missing", "denied"))

      runBlocking { service.tenantCapabilities(PRINCIPAL, UUID.randomUUID()) } shouldContainExactly
        emptyList()
    }
  }) {
  private companion object {
    val PRINCIPAL =
      AuthenticatedPrincipal(
        user =
          UserRecord(
            id = UUID.randomUUID(),
            apiId = PublicId.new("usr"),
            displayName = "Management user",
            primaryEmail = "management@example.test",
          ),
        loginAccountId = UUID.randomUUID(),
        sessionId = "session",
        bearerTokenId = null,
      )
  }
}
