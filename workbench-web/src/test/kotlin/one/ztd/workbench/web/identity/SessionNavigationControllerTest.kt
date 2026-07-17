package one.ztd.workbench.web.identity

import io.mockk.coEvery
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.application.permission.ManagementNavigationItem
import one.ztd.workbench.application.permission.ManagementNavigationService
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.auth.BearerTokenAuthenticator
import one.ztd.workbench.identity.auth.SessionAuthenticator
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.security.SecurityConfiguration
import one.ztd.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import one.ztd.workbench.security.WorkbenchAuthenticationFilter
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

@WebMvcTest(SessionNavigationController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  ContextWebMvcSupport::class,
  ProjectWebMvcSupport::class,
  SessionNavigationControllerTest.TestBeans::class,
)
class SessionNavigationControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `returns active tenant navigation`() {
    TestBeans.activeTenantId = TENANT_ID

    val result = authenticatedRequest()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.tenantContextStatus").value("ACTIVE"))
      .andExpect(jsonPath("$.items[0].id").value("MANAGEMENT_INSTANCE_OVERVIEW"))
      .andExpect(jsonPath("$.items[1].id").value("MANAGEMENT_TENANT_SETTINGS"))
  }

  @Test
  fun `returns not selected without evaluating tenant navigation`() {
    TestBeans.activeTenantId = null

    val result = authenticatedRequest()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.tenantContextStatus").value("NOT_SELECTED"))
      .andExpect(jsonPath("$.items.length()").value(1))
      .andExpect(jsonPath("$.items[0].id").value("MANAGEMENT_INSTANCE_OVERVIEW"))
  }

  @Test
  fun `rejects an unauthenticated navigation request`() {
    mockMvc.perform(get("/api/session/navigation")).andExpect(status().isUnauthorized())
  }

  private fun authenticatedRequest() =
    mockMvc
      .perform(
        get("/api/session/navigation").cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, SESSION_SECRET))
      )
      .andExpect(request().asyncStarted())
      .andReturn()

  @TestConfiguration
  class TestBeans {
    @Bean
    fun sessionAuthenticator(): SessionAuthenticator =
      object : SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String): AuthenticatedPrincipal? =
          PRINCIPAL.takeIf {
            sessionId == SESSION_SECRET
          }
      }

    @Bean
    fun bearerTokenAuthenticator(): BearerTokenAuthenticator =
      object : BearerTokenAuthenticator {
        override suspend fun authenticateBearerToken(token: String): AuthenticatedPrincipal? = null
      }

    @Bean fun clock(): Clock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC)

    @Bean
    fun sessionService(): SessionService = mockk {
      coEvery { activeTenantId(any()) } answers { activeTenantId }
    }

    @Bean
    fun navigationService(): ManagementNavigationService = mockk {
      coEvery { items(any(), any(), any()) } answers
        {
          val tenantId = secondArg<UUID?>()
          listOf(ManagementNavigationItem.MANAGEMENT_INSTANCE_OVERVIEW) +
            if (tenantId == null) {
              emptyList()
            } else {
              listOf(ManagementNavigationItem.MANAGEMENT_TENANT_SETTINGS)
            }
        }
    }

    companion object {
      var activeTenantId: UUID? = TENANT_ID
    }
  }

  private companion object {
    const val SESSION_SECRET = "navigation-session"
    val TENANT_ID: UUID = UUID.randomUUID()
    val PRINCIPAL =
      AuthenticatedPrincipal(
        user =
          UserRecord(
            id = UUID.randomUUID(),
            apiId = PublicId.new("usr"),
            displayName = "Navigation user",
            primaryEmail = "navigation@example.test",
          ),
        loginAccountId = UUID.randomUUID(),
        sessionId = UUID.randomUUID().toString(),
        bearerTokenId = null,
      )
  }
}
