package ink.doa.workbench.web.manage

import ink.doa.workbench.core.permission.model.PermissionService
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
import ink.doa.workbench.security.common.PublicIdResolver
import ink.doa.workbench.security.identity.SessionService
import ink.doa.workbench.security.permission.PermissionPolicyManagementService
import ink.doa.workbench.tenant.tenant.TenantOperationalGuard
import ink.doa.workbench.web.api.GlobalExceptionHandler
import ink.doa.workbench.web.api.InfrastructureAspect
import ink.doa.workbench.web.api.RequestContextResolver
import ink.doa.workbench.web.api.TenantRequestContextResolver
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ManagePermissionPolicyController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  AopAutoConfiguration::class,
  InfrastructureAspect::class,
  RequestContextResolver::class,
  TenantRequestContextResolver::class,
  ink.doa.workbench.web.support.ContextWebMvcSupport::class,
  ink.doa.workbench.web.support.ProjectWebMvcSupport::class,
  GlobalExceptionHandler::class,
  ManagePermissionPolicyControllerTest.TestBeans::class,
)
class ManagePermissionPolicyControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list permission policies rejects unauthenticated requests`() {
    mockMvc.perform(get("/api/manage/permission-policies")).andExpect(status().isUnauthorized())
  }

  @TestConfiguration
  class TestBeans {
    @Bean fun sessionService(): SessionService = mockk(relaxed = true)

    @Bean
    fun permissionPolicyManagementService(): PermissionPolicyManagementService =
      mockk(relaxed = true)

    @Bean fun permissionService(): PermissionService = mockk(relaxed = true)

    @Bean fun tenantOperationalGuard(): TenantOperationalGuard = mockk(relaxed = true)

    @Bean fun publicIdResolver(): PublicIdResolver = mockk(relaxed = true)

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

    @Bean
    fun clock(): java.time.Clock =
      java.time.Clock.fixed(
        java.time.Instant.parse("2026-07-04T00:00:00Z"),
        java.time.ZoneOffset.UTC,
      )
  }
}
