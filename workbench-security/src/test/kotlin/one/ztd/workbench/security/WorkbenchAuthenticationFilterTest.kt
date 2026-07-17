package one.ztd.workbench.security

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
import one.ztd.workbench.identity.auth.BearerTokenAuthenticator
import one.ztd.workbench.identity.auth.SessionAuthenticator
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.kernel.common.ids.PublicId
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockCookie
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class WorkbenchAuthenticationFilterTest :
  StringSpec({
    afterTest { SecurityContextHolder.clearContext() }

    "sets security context from bearer token" {
      val filter =
        WorkbenchAuthenticationFilter(
          sessionAuthenticator =
            object : SessionAuthenticator {
              override suspend fun authenticateSession(sessionId: String): AuthenticatedPrincipal? =
                null
            },
          bearerTokenAuthenticator =
            object : BearerTokenAuthenticator {
              override suspend fun authenticateBearerToken(token: String): AuthenticatedPrincipal? =
                if (token == "valid-token") PRINCIPAL else null
            },
        )
      val request =
        MockHttpServletRequest().apply {
          addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
        }

      filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

      SecurityContextHolder.getContext().authentication?.principal shouldBe PRINCIPAL
    }

    "invalid bearer token returns unauthorized without falling back to session cookie" {
      var sessionAuthenticatorCalled = false
      val filter =
        WorkbenchAuthenticationFilter(
          sessionAuthenticator =
            object : SessionAuthenticator {
              override suspend fun authenticateSession(sessionId: String): AuthenticatedPrincipal? {
                sessionAuthenticatorCalled = true
                return PRINCIPAL
              }
            },
          bearerTokenAuthenticator =
            object : BearerTokenAuthenticator {
              override suspend fun authenticateBearerToken(token: String): AuthenticatedPrincipal? =
                null
            },
        )
      val response = MockHttpServletResponse()
      val request =
        MockHttpServletRequest().apply {
          addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
          setCookies(MockCookie(WORKBENCH_SESSION_COOKIE_NAME, "valid-session"))
        }

      filter.doFilter(request, response, MockFilterChain())

      response.status shouldBe 401
      sessionAuthenticatorCalled shouldBe false
      SecurityContextHolder.getContext().authentication shouldBe null
    }
  }) {
  private companion object {
    val PRINCIPAL =
      AuthenticatedPrincipal(
        user =
          UserRecord(
            id = UUID.fromString("00000000-0000-0000-0000-000000000101"),
            apiId = PublicId.new("usr"),
            displayName = "Security Test",
            primaryEmail = "security@example.test",
          ),
        loginAccountId = UUID.fromString("00000000-0000-0000-0000-000000000102"),
        sessionId = null,
        bearerTokenId = "bearer-token-id",
      )
  }
}
