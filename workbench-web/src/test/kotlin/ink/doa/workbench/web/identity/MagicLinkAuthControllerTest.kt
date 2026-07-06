package ink.doa.workbench.web.identity

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.identity.model.LoginAccountRecord
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
import ink.doa.workbench.security.identity.FederatedLoginCompletionService
import ink.doa.workbench.security.identity.LoginContext
import ink.doa.workbench.security.identity.LoginView
import ink.doa.workbench.security.identity.SessionService
import ink.doa.workbench.security.identity.auth.MagicLinkAuthService
import ink.doa.workbench.security.identity.auth.MagicLinkIdentity
import ink.doa.workbench.web.api.GlobalExceptionHandler
import ink.doa.workbench.web.api.http.SessionCookieWriter
import ink.doa.workbench.web.support.ContextWebMvcSupport
import ink.doa.workbench.web.support.ProjectWebMvcSupport
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
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

@WebMvcTest(MagicLinkAuthController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  ContextWebMvcSupport::class,
  ProjectWebMvcSupport::class,
  GlobalExceptionHandler::class,
  MagicLinkAuthControllerTest.TestBeans::class,
)
class MagicLinkAuthControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `request magic link accepts payload`() {
    val result =
      mockMvc
        .perform(
          post("/api/auth/magic-link/request")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "email": "ada@example.test",
                "tenantId": "ten_01JABCDEFGHJKMNPQRSTVWXYZ2",
                "loginMethodId": "lmg_01JABCDEFGHJKMNPQRSTVWXYZ1"
              }
              """
                .trimIndent()
            )
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isAccepted())
  }

  @Test
  fun `verify magic link returns login response with session cookie`() {
    val result =
      mockMvc
        .perform(get("/api/auth/magic-link/verify").param("token", "opaque-token"))
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
    fun sessionAuthenticator(): ink.doa.workbench.core.identity.auth.SessionAuthenticator =
      object : ink.doa.workbench.core.identity.auth.SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String) = null
      }

    @Bean
    fun bearerTokenAuthenticator(): ink.doa.workbench.core.identity.auth.BearerTokenAuthenticator =
      object : ink.doa.workbench.core.identity.auth.BearerTokenAuthenticator {
        override suspend fun authenticateBearerToken(token: String) = null
      }

    @Bean fun clock(): Clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)

    @Bean fun sessionCookieWriter(clock: Clock): SessionCookieWriter = SessionCookieWriter(clock)

    @Bean fun sessionService(): SessionService = mockk(relaxed = true)

    @Bean
    fun publicIdResolver(): ink.doa.workbench.security.common.PublicIdResolver =
      mockk(relaxed = true)

    @Bean
    fun magicLinkAuthService(): MagicLinkAuthService = mockk {
      coEvery { requestMagicLink(any(), any(), any()) } returns Unit
      coEvery { resolveToken("opaque-token") } returns MAGIC_LINK_IDENTITY
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

    val MAGIC_LINK_IDENTITY =
      MagicLinkIdentity(
        user =
          UserRecord(
            id = USER_ID,
            apiId = ink.doa.workbench.core.common.ids.PublicId.new("usr"),
            displayName = "Ada Lovelace",
            primaryEmail = "ada@example.test",
          ),
        loginAccount =
          LoginAccountRecord(
            id = LOGIN_ACCOUNT_ID,
            apiId = ink.doa.workbench.core.common.ids.PublicId.new("lac"),
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
        tenantId = TENANT_ID,
      )
  }
}
