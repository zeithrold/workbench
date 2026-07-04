package ink.doa.workbench.web.admin

import ink.doa.workbench.core.identity.model.AuthenticatedPrincipal
import ink.doa.workbench.core.permission.AdminScope
import ink.doa.workbench.core.permission.model.AuthorizationDecision
import ink.doa.workbench.core.permission.model.AuthorizationScope
import ink.doa.workbench.core.permission.model.DecisionReason
import ink.doa.workbench.core.permission.model.PermissionService
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
import ink.doa.workbench.security.common.PublicIdResolver
import ink.doa.workbench.security.identity.SessionService
import ink.doa.workbench.security.permission.AccessGrantManagementService
import ink.doa.workbench.security.permission.AdminUserService
import ink.doa.workbench.security.permission.AdminUserView
import ink.doa.workbench.security.permission.PermissionActionService
import ink.doa.workbench.tenant.tenant.TenantOperationalGuard
import ink.doa.workbench.web.api.GlobalExceptionHandler
import ink.doa.workbench.web.api.InfrastructureAspect
import ink.doa.workbench.web.api.InstanceRequestContextResolver
import ink.doa.workbench.web.api.RequestContextResolver
import ink.doa.workbench.web.api.TenantRequestContextResolver
import ink.doa.workbench.web.support.ContextWebMvcSupport
import ink.doa.workbench.web.support.ProjectWebMvcSupport
import ink.doa.workbench.web.support.TenantWebMvcFixtures
import io.mockk.coEvery
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AdminUserController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  AopAutoConfiguration::class,
  InfrastructureAspect::class,
  RequestContextResolver::class,
  InstanceRequestContextResolver::class,
  TenantRequestContextResolver::class,
  ContextWebMvcSupport::class,
  ProjectWebMvcSupport::class,
  GlobalExceptionHandler::class,
  AdminUserControllerTest.TestBeans::class,
)
class AdminUserControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list instance admins rejects unauthenticated requests`() {
    mockMvc.perform(get("/api/admin/users/instance-admins")).andExpect(status().isUnauthorized())
  }

  @Test
  fun `list instance admins returns admins for authenticated instance user`() {
    val result =
      mockMvc
        .perform(
          get("/api/admin/users/instance-admins")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, ADMIN_SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].scope").value("instance"))
  }

  @Test
  fun `list tenant admins returns admins for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          get("/api/admin/users/tenant-admins")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].scope").value("tenant"))
  }

  @Test
  fun `list grants returns grants for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          get("/api/admin/users/grants")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].action").value("project.read"))
  }

  @Test
  fun `grant instance admin returns created admin for authenticated instance user`() {
    val result =
      mockMvc
        .perform(
          post("/api/admin/users/instance-admins")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, ADMIN_SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"userId":"usr_01JABCDEFGHJKMNPQRSTVWXYZ1"}""")
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.scope").value("instance"))
  }

  @TestConfiguration
  class TestBeans {
    @Bean
    fun sessionAuthenticator(): ink.doa.workbench.core.identity.auth.SessionAuthenticator =
      object : ink.doa.workbench.core.identity.auth.SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String): AuthenticatedPrincipal? =
          if (sessionId == ADMIN_SESSION || sessionId == TenantWebMvcFixtures.SESSION) {
            TenantWebMvcFixtures.PRINCIPAL
          } else {
            null
          }
      }

    @Bean
    fun bearerTokenAuthenticator(): ink.doa.workbench.core.identity.auth.BearerTokenAuthenticator =
      object : ink.doa.workbench.core.identity.auth.BearerTokenAuthenticator {
        override suspend fun authenticateBearerToken(token: String) = null
      }

    @Bean
    fun sessionService(): SessionService = mockk {
      coEvery { requireActiveTenant(any()) } returns TenantWebMvcFixtures.TENANT_RECORD
    }

    @Bean fun adminUserService(): AdminUserService = mockk(relaxed = true)

    @Bean fun accessGrantService(): AccessGrantManagementService = mockk(relaxed = true)

    @Bean fun permissionActionService(): PermissionActionService = mockk(relaxed = true)

    @Bean fun publicIdResolver(): PublicIdResolver = mockk(relaxed = true)

    @Bean
    fun tenantOperationalGuard(): TenantOperationalGuard = mockk {
      coEvery { ensureOperational(any()) } returns Unit
    }

    @Bean
    fun permissionService(): PermissionService =
      object : PermissionService {
        override suspend fun decide(
          request: ink.doa.workbench.core.permission.model.AuthorizationRequest
        ): AuthorizationDecision =
          if (
            request.scope == AuthorizationScope.INSTANCE &&
              request.subject.userId == TenantWebMvcFixtures.USER_ID
          ) {
            AuthorizationDecision.Allow(DecisionReason("grant_allowed", "allowed"))
          } else if (
            request.scope == AuthorizationScope.TENANT &&
              request.subject.userId == TenantWebMvcFixtures.USER_ID
          ) {
            AuthorizationDecision.Allow(DecisionReason("grant_allowed", "allowed"))
          } else {
            AuthorizationDecision.Deny(DecisionReason("grant_denied", "denied"))
          }
      }

    @Bean
    fun clock(): java.time.Clock =
      java.time.Clock.fixed(
        java.time.Instant.parse("2026-07-04T00:00:00Z"),
        java.time.ZoneOffset.UTC,
      )

    @Bean
    fun adminUserServiceSetup(service: AdminUserService): Boolean {
      coEvery { service.listInstanceAdmins() } returns
        listOf(
          AdminUserView(
            id = "adm_01JABCDEFGHJKMNPQRSTVWXYZ0",
            userId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
            scope = AdminScope.INSTANCE,
            tenantId = null,
            status = "active",
            validFrom = java.time.OffsetDateTime.parse("2026-07-04T00:00:00Z"),
            validTo = null,
          )
        )
      coEvery { service.listTenantAdmins(TenantWebMvcFixtures.TENANT_ID) } returns
        listOf(
          AdminUserView(
            id = "adm_01JABCDEFGHJKMNPQRSTVWXYZ1",
            userId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
            scope = AdminScope.TENANT,
            tenantId = TenantWebMvcFixtures.TENANT_ID.toString(),
            status = "active",
            validFrom = java.time.OffsetDateTime.parse("2026-07-04T00:00:00Z"),
            validTo = null,
          )
        )
      coEvery { service.grantInstanceAdmin("usr_01JABCDEFGHJKMNPQRSTVWXYZ1", any()) } returns
        AdminUserView(
          id = "adm_01JABCDEFGHJKMNPQRSTVWXYZ2",
          userId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
          scope = AdminScope.INSTANCE,
          tenantId = null,
          status = "active",
          validFrom = java.time.OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          validTo = null,
        )
      return true
    }

    @Bean
    fun accessGrantServiceSetup(service: AccessGrantManagementService): Boolean {
      coEvery {
        service.listGrants(
          scope = null,
          tenantId = TenantWebMvcFixtures.TENANT_ID,
          subjectUserId = null,
        )
      } returns
        listOf(
          ink.doa.workbench.security.permission.AccessGrantView(
            id = "grt_01JABCDEFGHJKMNPQRSTVWXYZ0",
            scope = ink.doa.workbench.core.permission.GrantScope.TENANT,
            tenantId = TenantWebMvcFixtures.TENANT_ID.toString(),
            projectId = null,
            userId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
            action = "project.read",
            resourcePattern = "project:*",
            effect = ink.doa.workbench.core.permission.model.PermissionEffect.ALLOW,
            validFrom = java.time.OffsetDateTime.parse("2026-07-04T00:00:00Z"),
            validTo = null,
          )
        )
      return true
    }
  }

  private companion object {
    const val ADMIN_SESSION = "admin-session"
  }
}
