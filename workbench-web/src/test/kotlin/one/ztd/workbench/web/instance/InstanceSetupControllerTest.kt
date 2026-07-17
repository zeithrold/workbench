package one.ztd.workbench.web.instance

import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import one.ztd.workbench.application.instance.InstanceBootstrapView
import one.ztd.workbench.application.instance.InstanceSetupApplicationService
import one.ztd.workbench.application.instance.InstanceSetupStatusView
import one.ztd.workbench.identity.LoginView
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.common.summary.LoginMethodSummary
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.security.SecurityConfiguration
import one.ztd.workbench.security.WorkbenchAuthenticationFilter
import one.ztd.workbench.web.api.http.SessionCookieWriter
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

@WebMvcTest(InstanceSetupController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  one.ztd.workbench.web.support.ContextWebMvcSupport::class,
  one.ztd.workbench.web.support.ProjectWebMvcSupport::class,
  InstanceSetupControllerTest.TestBeans::class,
)
class InstanceSetupControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `setup status is public`() {
    val result =
      mockMvc
        .perform(get("/api/instance/setup-status"))
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.initialized").value(false))
      .andExpect(jsonPath("$.setupTokenRequired").value(true))
  }

  @Test
  fun `setup creates administrator without prior authentication`() {
    val result =
      mockMvc
        .perform(
          post("/api/instance/setup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "displayName": "Admin",
                "email": "admin@example.test",
                "password": "secure-password-1"
              }
              """
                .trimIndent()
            )
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.user.displayName").value("Admin"))
      .andExpect(jsonPath("$.loginMethod.code").value("password"))
      .andExpect(header().exists("Set-Cookie"))
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

    @Bean fun clock(): Clock = Clock.fixed(Instant.parse("2026-07-03T00:00:00Z"), ZoneOffset.UTC)

    @Bean fun sessionCookieWriter(clock: Clock): SessionCookieWriter = SessionCookieWriter(clock)

    @Bean fun sessionService(): SessionService = mockk(relaxed = true)

    @Bean
    fun publicIdResolver(): one.ztd.workbench.application.identity.PublicIdResolver =
      mockk(relaxed = true)

    @Bean
    fun instanceSetupService(): InstanceSetupApplicationService = mockk {
      coEvery { setupStatus() } returns
        InstanceSetupStatusView(initialized = false, setupTokenRequired = true)
      coEvery { bootstrap(any()) } returns
        InstanceBootstrapView(
          user =
            UserSummary(
              id = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ0"),
              displayName = "Admin",
              primaryEmail = "admin@example.test",
            ),
          loginMethod =
            LoginMethodSummary(
              id = "lmg_01JABCDEFGHJKMNPQRSTVWXYZ1",
              code = "password",
              kind = LoginMethodKind.PASSWORD,
              name = "Password",
            ),
          session =
            LoginView(
              user =
                UserSummary(
                  id = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ0"),
                  displayName = "Admin",
                  primaryEmail = "admin@example.test",
                ),
              sessionExpiresAt = OffsetDateTime.parse("2026-07-03T12:00:00Z"),
              sessionSecret = "session-secret",
              bearerToken = null,
            ),
        )
    }
  }
}
