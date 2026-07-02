package doa.ink.workbench.security

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.identity.auth.BearerTokenAuthenticator
import doa.ink.workbench.core.identity.auth.SessionAuthenticator
import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.core.identity.model.UserRecord
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
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
