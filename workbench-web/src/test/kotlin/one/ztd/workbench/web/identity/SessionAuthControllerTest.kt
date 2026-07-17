package one.ztd.workbench.web.identity

import io.mockk.coEvery
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.identity.AuthApplicationService
import one.ztd.workbench.identity.LoginContext
import one.ztd.workbench.identity.LoginView
import one.ztd.workbench.identity.MembershipService
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.TenantMembershipView
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.security.SecurityConfiguration
import one.ztd.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import one.ztd.workbench.security.WorkbenchAuthenticationFilter
import one.ztd.workbench.tenant.common.summary.TenantSummary
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

@WebMvcTest(SessionAuthController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  ContextWebMvcSupport::class,
  ProjectWebMvcSupport::class,
  GlobalExceptionHandler::class,
  SessionAuthControllerTest.TestBeans::class,
)
class SessionAuthControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `login returns session cookie and user payload`() {
    val result =
      mockMvc
        .perform(
          post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "method": "PASSWORD",
                "loginMethodId": "lmg_01JABCDEFGHJKMNPQRSTVWXYZ1",
                "tenantId": "ten_01JABCDEFGHJKMNPQRSTVWXYZ2",
                "subject": "ada@example.test",
                "password": "secret"
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
      .andExpect(jsonPath("$.user.displayName").value("Ada Lovelace"))
      .andExpect(
        header()
          .string("Set-Cookie", org.hamcrest.Matchers.containsString(WORKBENCH_SESSION_COOKIE_NAME))
      )
  }

  @Test
  fun `logout clears session cookie`() {
    val result =
      mockMvc
        .perform(
          post("/api/auth/logout").cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, "session-secret"))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isNoContent())
      .andExpect(
        header()
          .string("Set-Cookie", org.hamcrest.Matchers.containsString(WORKBENCH_SESSION_COOKIE_NAME))
      )
  }

  @Test
  fun `memberships requires authentication`() {
    mockMvc.perform(get("/api/auth/memberships")).andExpect(status().isUnauthorized())
  }

  @Test
  fun `memberships returns active memberships for authenticated user`() {
    val result =
      mockMvc
        .perform(
          get("/api/auth/memberships")
            .cookie(
              Cookie(
                WORKBENCH_SESSION_COOKIE_NAME,
                SessionControllerSecurityTestFixtures.SESSION_SECRET,
              )
            )
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].tenant.slug").value("acme"))
      .andExpect(jsonPath("$[0].isTenantAdmin").value(true))
  }

  @TestConfiguration
  class TestBeans {
    @Bean
    fun sessionAuthenticator(): one.ztd.workbench.identity.auth.SessionAuthenticator =
      object : one.ztd.workbench.identity.auth.SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String) =
          if (sessionId == SessionControllerSecurityTestFixtures.SESSION_SECRET) {
            SessionControllerSecurityTestFixtures.PRINCIPAL
          } else {
            null
          }
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
    fun authApplicationService(): AuthApplicationService = mockk {
      coEvery { login(any()) } returns
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
      coEvery { logout(any(), any(), any()) } returns Unit
    }

    @Bean
    fun membershipService(): MembershipService = mockk {
      coEvery { listActiveMemberships(any()) } returns
        listOf(
          TenantMembershipView(
            id = "mbr_01JABCDEFGHJKMNPQRSTVWXYZ3",
            tenant =
              TenantSummary(
                id = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ2"),
                slug = "acme",
                name = "Acme",
              ),
            isTenantAdmin = true,
          )
        )
    }
  }
}

internal object SessionControllerSecurityTestFixtures {
  val USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
  val LOGIN_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
  val SESSION_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
  const val SESSION_SECRET = "session-secret"
  val PRINCIPAL =
    one.ztd.workbench.identity.model.AuthenticatedPrincipal(
      user =
        one.ztd.workbench.identity.model.UserRecord(
          id = USER_ID,
          apiId = one.ztd.workbench.kernel.common.ids.PublicId.new("usr"),
          displayName = "Ada Lovelace",
          primaryEmail = "ada@example.test",
        ),
      loginAccountId = LOGIN_ACCOUNT_ID,
      sessionId = SESSION_ID.toString(),
      bearerTokenId = null,
    )
}
