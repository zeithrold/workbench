package ink.doa.workbench.web.instance

import ink.doa.workbench.core.common.summary.LoginMethodSummary
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.identity.model.LoginMethodKind
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
import ink.doa.workbench.security.identity.LoginView
import ink.doa.workbench.security.identity.SessionService
import ink.doa.workbench.service.instance.InstanceBootstrapView
import ink.doa.workbench.service.instance.InstanceSetupApplicationService
import ink.doa.workbench.service.instance.InstanceSetupStatusView
import ink.doa.workbench.web.api.http.SessionCookieWriter
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
  ink.doa.workbench.web.support.ContextWebMvcSupport::class,
  ink.doa.workbench.web.support.ProjectWebMvcSupport::class,
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
    fun sessionAuthenticator(): ink.doa.workbench.core.identity.auth.SessionAuthenticator =
      object : ink.doa.workbench.core.identity.auth.SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String) = null
      }

    @Bean
    fun bearerTokenAuthenticator(): ink.doa.workbench.core.identity.auth.BearerTokenAuthenticator =
      object : ink.doa.workbench.core.identity.auth.BearerTokenAuthenticator {
        override suspend fun authenticateBearerToken(token: String) = null
      }

    @Bean fun clock(): Clock = Clock.fixed(Instant.parse("2026-07-03T00:00:00Z"), ZoneOffset.UTC)

    @Bean fun sessionCookieWriter(clock: Clock): SessionCookieWriter = SessionCookieWriter(clock)

    @Bean fun sessionService(): SessionService = mockk(relaxed = true)

    @Bean
    fun publicIdResolver(): ink.doa.workbench.security.common.PublicIdResolver =
      mockk(relaxed = true)

    @Bean
    fun instanceSetupService(): InstanceSetupApplicationService = mockk {
      coEvery { setupStatus() } returns InstanceSetupStatusView(initialized = false)
      coEvery { bootstrap(any()) } returns
        InstanceBootstrapView(
          user =
            UserSummary(
              id = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
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
                  id = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
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
