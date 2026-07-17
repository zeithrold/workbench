package one.ztd.workbench.web.identity

import io.mockk.coEvery
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import one.ztd.workbench.identity.AuthApplicationService
import one.ztd.workbench.identity.IssuedTokenView
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.security.SecurityConfiguration
import one.ztd.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import one.ztd.workbench.security.WorkbenchAuthenticationFilter
import one.ztd.workbench.web.api.GlobalExceptionHandler
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(BearerTokenAuthController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  ContextWebMvcSupport::class,
  ProjectWebMvcSupport::class,
  GlobalExceptionHandler::class,
  BearerTokenAuthControllerTest.TestBeans::class,
)
class BearerTokenAuthControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `create token requires authentication`() {
    mockMvc
      .perform(
        post("/api/auth/tokens")
          .contentType(MediaType.APPLICATION_JSON)
          .content("""{"name":"CI pipeline"}""")
      )
      .andExpect(status().isUnauthorized())
  }

  @Test
  fun `create token returns issued credential`() {
    val result =
      mockMvc
        .perform(
          post("/api/auth/tokens")
            .cookie(
              Cookie(
                WORKBENCH_SESSION_COOKIE_NAME,
                SessionControllerSecurityTestFixtures.SESSION_SECRET,
              )
            )
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "tenantId": "ten_01JABCDEFGHJKMNPQRSTVWXYZ2",
                "name": "CI pipeline",
                "scopes": ["workbench.api"]
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
      .andExpect(jsonPath("$.id").value("btk_01JABCDEFGHJKMNPQRSTVWXYZ4"))
      .andExpect(jsonPath("$.token").value("opaque-token-secret"))
  }

  @Test
  fun `revoke token returns no content`() {
    val result =
      mockMvc
        .perform(
          delete("/api/auth/tokens/btk_01JABCDEFGHJKMNPQRSTVWXYZ4")
            .cookie(
              Cookie(
                WORKBENCH_SESSION_COOKIE_NAME,
                SessionControllerSecurityTestFixtures.SESSION_SECRET,
              )
            )
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent())
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

    @Bean fun sessionService(): SessionService = mockk(relaxed = true)

    @Bean
    fun publicIdResolver(): one.ztd.workbench.application.identity.PublicIdResolver =
      mockk(relaxed = true)

    @Bean
    fun authApplicationService(): AuthApplicationService = mockk {
      coEvery { issueBearerToken(any(), any(), any(), any(), any()) } returns
        IssuedTokenView(
          id = "btk_01JABCDEFGHJKMNPQRSTVWXYZ4",
          token = "opaque-token-secret",
          expiresAt = OffsetDateTime.parse("2027-07-04T12:00:00Z"),
        )
      coEvery { revokeBearerToken(any(), any(), any()) } returns Unit
    }
  }
}
