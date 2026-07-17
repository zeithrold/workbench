package one.ztd.workbench.web.identity

import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import one.ztd.workbench.identity.LoginDiscoveryRepository
import one.ztd.workbench.identity.LoginDiscoveryService
import one.ztd.workbench.identity.LoginDiscoveryView
import one.ztd.workbench.identity.LoginFlow
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.security.SecurityConfiguration
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(LoginDiscoveryController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  ContextWebMvcSupport::class,
  ProjectWebMvcSupport::class,
  GlobalExceptionHandler::class,
  LoginDiscoveryControllerTest.TestBeans::class,
)
class LoginDiscoveryControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `login options returns mapped login methods`() {
    val result =
      mockMvc
        .perform(get("/api/auth/login-options").param("identifier", "ada@example.test"))
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].loginMethod.code").value("password"))
  }

  @Test
  fun `login discovery returns flow type`() {
    val result =
      mockMvc
        .perform(get("/api/auth/login-discovery").param("identifier", "ada@example.test"))
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.flow").value("TENANT"))
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

    @Bean fun sessionService(): SessionService = mockk(relaxed = true)

    @Bean
    fun publicIdResolver(): one.ztd.workbench.application.identity.PublicIdResolver =
      mockk(relaxed = true)

    @Bean
    fun loginDiscoveryRepository(): LoginDiscoveryRepository = mockk {
      coEvery { listLoginOptionsForIdentifier("ada@example.test") } returns
        listOf(
          one.ztd.workbench.identity.model.TenantLoginOption(
            tenant =
              one.ztd.workbench.tenant.common.summary.TenantSummary(
                id = PublicId.new("ten"),
                slug = "acme",
                name = "Acme",
              ),
            loginMethod =
              one.ztd.workbench.identity.common.summary.LoginMethodSummary(
                id = "lmd_abc",
                code = "password",
                kind = LoginMethodKind.PASSWORD,
                name = "Password",
              ),
          )
        )
    }

    @Bean
    fun loginDiscoveryService(): LoginDiscoveryService = mockk {
      coEvery { discover("ada@example.test") } returns
        LoginDiscoveryView(
          identifierRecognized = true,
          flow = LoginFlow.TENANT,
          instancePasswordMethod = null,
          tenantMethods = emptyList(),
        )
    }
  }
}
