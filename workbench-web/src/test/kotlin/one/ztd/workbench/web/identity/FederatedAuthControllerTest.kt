package one.ztd.workbench.web.identity

import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.identity.FederatedLoginCompletionService
import one.ztd.workbench.identity.LoginContext
import one.ztd.workbench.identity.LoginView
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.auth.FederatedAuthService
import one.ztd.workbench.identity.auth.FederatedAuthorizeResult
import one.ztd.workbench.identity.auth.FederatedLoginResult
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.identity.model.AuthenticatedIdentity
import one.ztd.workbench.identity.model.LoginAccountRecord
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.security.SecurityConfiguration
import one.ztd.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import one.ztd.workbench.security.WorkbenchAuthenticationFilter
import one.ztd.workbench.web.api.GlobalExceptionHandler
import one.ztd.workbench.web.api.http.SessionCookieWriter
import one.ztd.workbench.web.support.ContextWebMvcSupport
import one.ztd.workbench.web.support.ProjectWebMvcSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(FederatedAuthController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  ContextWebMvcSupport::class,
  ProjectWebMvcSupport::class,
  GlobalExceptionHandler::class,
  FederatedAuthControllerTest.TestBeans::class,
)
class FederatedAuthControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `federated authorize returns authorization url`() {
    val result =
      mockMvc
        .perform(
          post("/api/auth/federated/authorize")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "loginMethodId": "lmg_01JABCDEFGHJKMNPQRSTVWXYZ1",
                "tenantId": "ten_01JABCDEFGHJKMNPQRSTVWXYZ2",
                "returnUrl": "https://app.example.com/auth/callback"
              }
              """
                .trimIndent()
            )
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.authorizationUrl").value("https://idp.example.com/authorize"))
      .andExpect(jsonPath("$.state").value("opaque-state"))
  }

  @Test
  fun `oauth callback completes login`() {
    val result =
      mockMvc
        .perform(
          get("/api/auth/oauth2/callback").param("code", "auth-code").param("state", "opaque-state")
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.user.displayName").value("Ada Lovelace"))
      .andExpect(
        header()
          .string("Set-Cookie", org.hamcrest.Matchers.containsString(WORKBENCH_SESSION_COOKIE_NAME))
      )
  }

  @Test
  fun `saml acs completes login`() {
    val result =
      mockMvc
        .perform(
          post("/api/auth/saml2/acs")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("SAMLResponse", "base64-saml")
            .param("RelayState", "opaque-state")
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.user.displayName").value("Ada Lovelace"))
      .andExpect(
        header()
          .string("Set-Cookie", org.hamcrest.Matchers.containsString(WORKBENCH_SESSION_COOKIE_NAME))
      )
  }

  @TestConfiguration
  class TestBeans {
    @Bean
    fun sessionAuthenticator(): one.ztd.workbench.identity.auth.SessionAuthenticator =
      object : one.ztd.workbench.identity.auth.SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String) = null
      }

    @Bean
    fun bearerTokenAuthenticator(): one.ztd.workbench.identity.auth.BearerTokenAuthenticator =
      object : one.ztd.workbench.identity.auth.BearerTokenAuthenticator {
        override suspend fun authenticateBearerToken(token: String) = null
      }

    @Bean fun clock(): Clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)

    @Bean fun sessionCookieWriter(clock: Clock): SessionCookieWriter = SessionCookieWriter(clock)

    @Bean fun sessionService(): SessionService = mockk(relaxed = true)

    @Bean
    fun publicIdResolver(): one.ztd.workbench.application.identity.PublicIdResolver =
      mockk(relaxed = true)

    @Bean
    fun federatedAuthService(): FederatedAuthService = mockk {
      coEvery { beginAuthorize(any(), any(), any(), any()) } returns
        FederatedAuthorizeResult(
          authorizationUrl = "https://idp.example.com/authorize",
          state = "opaque-state",
        )
      coEvery { completeOAuthCallback(any(), any(), any()) } returns FEDERATED_LOGIN_RESULT
      coEvery { completeSamlAcs(any(), any()) } returns FEDERATED_LOGIN_RESULT
    }

    @Bean
    fun federatedLoginCompletionService(): FederatedLoginCompletionService = mockk {
      coEvery { complete(any(), any()) } returns
        LoginView(
          user =
            UserSummary(
              id = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ0"),
              displayName = "Ada Lovelace",
              primaryEmail = "ada@example.test",
            ),
          sessionExpiresAt = OffsetDateTime.parse("2026-07-04T12:00:00Z"),
          sessionSecret = "session-secret",
          bearerToken = null,
          loginContext = LoginContext.TENANT,
        )
    }
  }

  private companion object {
    val USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val LOGIN_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val TENANT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000010")

    val FEDERATED_LOGIN_RESULT =
      FederatedLoginResult(
        identity =
          AuthenticatedIdentity(
            user =
              UserRecord(
                id = USER_ID,
                apiId = one.ztd.workbench.kernel.common.ids.PublicId.new("usr"),
                displayName = "Ada Lovelace",
                primaryEmail = "ada@example.test",
              ),
            loginAccount =
              LoginAccountRecord(
                id = LOGIN_ACCOUNT_ID,
                apiId = one.ztd.workbench.kernel.common.ids.PublicId.new("lac"),
                loginMethodId = UUID.randomUUID(),
                subject = "ada@example.test",
                normalizedSubject = "ada@example.test",
                displayName = "Ada Lovelace",
                lastUsedAt = null,
                disabledAt = null,
                disabledBy = null,
                createdAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
                updatedAt = OffsetDateTime.parse("2026-07-01T00:00:00Z"),
              ),
          ),
        tenantId = TENANT_ID,
      )
  }
}
