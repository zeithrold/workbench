package ink.doa.workbench.web.identity

import ink.doa.workbench.identity.LoginDiscoveryRepository
import ink.doa.workbench.identity.LoginDiscoveryService
import ink.doa.workbench.identity.LoginDiscoveryView
import ink.doa.workbench.identity.LoginFlow
import ink.doa.workbench.identity.SessionService
import ink.doa.workbench.identity.model.LoginMethodKind
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
import ink.doa.workbench.web.api.GlobalExceptionHandler
import ink.doa.workbench.web.support.ContextWebMvcSupport
import ink.doa.workbench.web.support.ProjectWebMvcSupport
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
    fun sessionAuthenticator(): ink.doa.workbench.identity.auth.SessionAuthenticator =
      object : ink.doa.workbench.identity.auth.SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String) = null
      }

    @Bean
    fun bearerTokenAuthenticator(): ink.doa.workbench.identity.auth.BearerTokenAuthenticator =
      object : ink.doa.workbench.identity.auth.BearerTokenAuthenticator {
        override suspend fun authenticateBearerToken(token: String) = null
      }

    @Bean fun clock(): Clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)

    @Bean fun sessionService(): SessionService = mockk(relaxed = true)

    @Bean
    fun publicIdResolver(): ink.doa.workbench.application.identity.PublicIdResolver =
      mockk(relaxed = true)

    @Bean
    fun loginDiscoveryRepository(): LoginDiscoveryRepository = mockk {
      coEvery { listLoginOptionsForIdentifier("ada@example.test") } returns
        listOf(
          ink.doa.workbench.identity.model.TenantLoginOption(
            tenant =
              ink.doa.workbench.tenant.common.summary.TenantSummary(
                id = PublicId.new("ten"),
                slug = "acme",
                name = "Acme",
              ),
            loginMethod =
              ink.doa.workbench.identity.common.summary.LoginMethodSummary(
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
