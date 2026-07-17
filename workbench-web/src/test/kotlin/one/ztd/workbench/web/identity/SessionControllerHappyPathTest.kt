package one.ztd.workbench.web.identity

import io.mockk.coEvery
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import one.ztd.workbench.identity.LocaleContextView
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.SessionView
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.security.SecurityConfiguration
import one.ztd.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import one.ztd.workbench.security.WorkbenchAuthenticationFilter
import one.ztd.workbench.tenant.common.summary.TenantSummary
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(SessionController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  ContextWebMvcSupport::class,
  ProjectWebMvcSupport::class,
  SessionControllerHappyPathTest.TestBeans::class,
)
class SessionControllerHappyPathTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `switch tenant returns updated session`() {
    val result =
      mockMvc
        .perform(
          patch("/api/session")
            .cookie(
              Cookie(
                WORKBENCH_SESSION_COOKIE_NAME,
                SessionControllerSecurityTestFixtures.SESSION_SECRET,
              )
            )
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"tenantId":"ten_01JABCDEFGHJKMNPQRSTVWXYZ2"}""")
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.activeTenant.slug").value("acme"))
      .andExpect(jsonPath("$.localeContext.tenantDefault").value("en-US"))
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

    @Bean
    fun publicIdResolver(): one.ztd.workbench.application.identity.PublicIdResolver =
      mockk(relaxed = true)

    @Bean
    fun sessionService(): SessionService = mockk {
      coEvery { switchTenant(any(), any()) } returns
        SessionView(
          user =
            UserSummary(
              id = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ0"),
              displayName = "Ada Lovelace",
              primaryEmail = "ada@example.test",
            ),
          activeTenant =
            TenantSummary(
              id = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ2"),
              slug = "acme",
              name = "Acme",
            ),
          sessionExpiresAt = OffsetDateTime.parse("2026-07-04T12:00:00Z"),
          localeContext = LocaleContextView(tenantDefault = "en-US"),
        )
    }
  }
}
