package one.ztd.workbench.application.permission

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
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
import one.ztd.workbench.identity.permission.model.DecisionReason
import one.ztd.workbench.identity.permission.model.PermissionService
import one.ztd.workbench.kernel.common.ids.PublicId

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
            firstArg<one.ztd.workbench.identity.permission.model.AuthorizationRequest>().action.code
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
