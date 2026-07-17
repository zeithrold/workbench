package one.ztd.workbench.application.permission

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.identity.permission.model.AuthorizationDecision
import one.ztd.workbench.identity.permission.model.AuthorizationRequest
import one.ztd.workbench.identity.permission.model.AuthorizationScope
import one.ztd.workbench.identity.permission.model.DecisionReason
import one.ztd.workbench.identity.permission.model.PermissionService
import one.ztd.workbench.kernel.common.ids.PublicId

class ManagementNavigationServiceTest :
  StringSpec({
    val permissions = mockk<PermissionService>()
    val service =
      ManagementNavigationService(
        permissions,
        Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC),
      )

    "returns navigation items for allowed fixed checks" {
      var captured = emptyList<AuthorizationRequest>()
      coEvery { permissions.decideAll(any()) } answers
        {
          captured = firstArg()
          captured.map { request ->
            if (
              request.action.code == "operations.read" || request.scope == AuthorizationScope.TENANT
            ) {
              allow()
            } else {
              deny()
            }
          }
        }

      service.items(PRINCIPAL, TENANT_ID, "request-id") shouldContainExactly
        listOf(
          ManagementNavigationItem.MANAGEMENT_INSTANCE_OVERVIEW,
          ManagementNavigationItem.MANAGEMENT_INSTANCE_OPERATIONS,
          ManagementNavigationItem.MANAGEMENT_TENANT_SETTINGS,
          ManagementNavigationItem.MANAGEMENT_TENANT_MEMBERS,
          ManagementNavigationItem.MANAGEMENT_TENANT_ADMINISTRATORS,
          ManagementNavigationItem.MANAGEMENT_TENANT_ACCESS,
        )
      captured.size shouldBe 5
      captured.map { it.action.code }.count { it == "operations.read" } shouldBe 1
    }

    "does not evaluate tenant navigation without an active tenant" {
      var captured = emptyList<AuthorizationRequest>()
      coEvery { permissions.decideAll(any()) } answers
        {
          captured = firstArg()
          captured.map { allow() }
        }

      service.items(PRINCIPAL, null, "request-id") shouldContainExactly
        listOf(
          ManagementNavigationItem.MANAGEMENT_INSTANCE_OVERVIEW,
          ManagementNavigationItem.MANAGEMENT_INSTANCE_TENANTS,
          ManagementNavigationItem.MANAGEMENT_INSTANCE_ADMINISTRATORS,
          ManagementNavigationItem.MANAGEMENT_INSTANCE_OPERATIONS,
          ManagementNavigationItem.MANAGEMENT_INSTANCE_OUTBOX,
        )
      captured.size shouldBe 4
      captured.all { it.scope == AuthorizationScope.INSTANCE } shouldBe true
    }

    "returns an empty list when all navigation checks are denied" {
      coEvery { permissions.decideAll(any()) } answers
        {
          firstArg<List<AuthorizationRequest>>().map { deny() }
        }

      service.items(PRINCIPAL, TENANT_ID, "request-id") shouldContainExactly emptyList()
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
            displayName = "Navigation user",
            primaryEmail = "navigation@example.test",
          ),
        loginAccountId = UUID.randomUUID(),
        sessionId = UUID.randomUUID().toString(),
        bearerTokenId = null,
      )

    fun allow(): AuthorizationDecision =
      AuthorizationDecision.Allow(DecisionReason("allowed", "Allowed"))

    fun deny(): AuthorizationDecision =
      AuthorizationDecision.Deny(DecisionReason("denied", "Denied"))
  }
}
