package ink.doa.workbench.web.identity

import ink.doa.workbench.core.common.summary.LoginMethodSummary
import ink.doa.workbench.core.common.summary.TenantSummary
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.identity.model.LoginMethodKind
import ink.doa.workbench.security.identity.FederatedAuthorizeView
import ink.doa.workbench.security.identity.IssuedTokenView
import ink.doa.workbench.security.identity.LoginDiscoveryView
import ink.doa.workbench.security.identity.LoginFlow
import ink.doa.workbench.security.identity.LoginMethodChoiceView
import ink.doa.workbench.security.identity.LoginOptionView
import ink.doa.workbench.security.identity.LoginView
import ink.doa.workbench.security.identity.TenantMembershipView
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime

class AuthResponsesTest :
  StringSpec({
    val user = UserSummary(id = "usr_abc", displayName = "Ada", primaryEmail = "ada@example.test")
    val tenant = TenantSummary(id = "ten_abc", name = "Acme", slug = "acme")
    val expiresAt = OffsetDateTime.parse("2026-07-04T12:00:00Z")

    "login response maps login view" {
      val response =
        LoginResponse.from(
          LoginView(
            user = user,
            sessionSecret = "session-secret",
            sessionExpiresAt = expiresAt,
            bearerToken = IssuedTokenView(id = "btk_abc", token = "secret", expiresAt = expiresAt),
            loginContext = ink.doa.workbench.security.identity.LoginContext.TENANT,
            activeTenant = tenant,
            eligibleTenants = listOf(tenant),
          )
        )

      response.user.displayName shouldBe "Ada"
      response.bearerToken?.token shouldBe "secret"
    }

    "membership response maps tenant membership view" {
      MembershipResponse.from(
          TenantMembershipView(id = "tmb_abc", tenant = tenant, isTenantAdmin = true)
        )
        .isTenantAdmin shouldBe true
    }

    "login discovery response maps discovery view" {
      val method =
        LoginMethodSummary(
          id = "lmd_abc",
          code = "password",
          kind = LoginMethodKind.PASSWORD,
          name = "Password",
        )
      LoginDiscoveryResponse.from(
          LoginDiscoveryView(
            identifierRecognized = true,
            flow = LoginFlow.TENANT,
            instancePasswordMethod = null,
            tenantMethods =
              listOf(
                LoginMethodChoiceView(loginMethod = method, supportedTenants = listOf(tenant))
              ),
          )
        )
        .tenantMethods
        .single()
        .loginMethod
        .code shouldBe "password"
    }

    "login option and federated authorize responses map views" {
      val method =
        LoginMethodSummary(
          id = "lmd_abc",
          code = "oidc",
          kind = LoginMethodKind.OIDC,
          name = "OIDC",
        )
      LoginOptionResponse.from(LoginOptionView(tenant = tenant, loginMethod = method))
        .tenant
        .slug shouldBe "acme"
      FederatedAuthorizeResponse.from(
          FederatedAuthorizeView(
            authorizationUrl = "https://idp.example.test/auth",
            state = "state-1",
          )
        )
        .state shouldBe "state-1"
    }
  })
