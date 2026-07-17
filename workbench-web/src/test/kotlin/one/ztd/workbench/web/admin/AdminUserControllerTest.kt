package one.ztd.workbench.web.admin

import io.mockk.coEvery
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import one.ztd.workbench.application.identity.PublicIdResolver
import one.ztd.workbench.application.permission.AccessGrantManagementService
import one.ztd.workbench.application.permission.AccessGrantView
import one.ztd.workbench.application.permission.AdminUserService
import one.ztd.workbench.application.permission.AdminUserView
import one.ztd.workbench.application.permission.CreateManagedAccessGrantCommand
import one.ztd.workbench.application.permission.PermissionActionService
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.permission.AdminScope
import one.ztd.workbench.identity.permission.model.AuthorizationDecision
import one.ztd.workbench.identity.permission.model.AuthorizationScope
import one.ztd.workbench.identity.permission.model.DecisionReason
import one.ztd.workbench.identity.permission.model.PermissionService
import one.ztd.workbench.security.SecurityConfiguration
import one.ztd.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import one.ztd.workbench.security.WorkbenchAuthenticationFilter
import one.ztd.workbench.tenant.tenant.TenantOperationalGuard
import one.ztd.workbench.web.api.GlobalExceptionHandler
import one.ztd.workbench.web.api.InfrastructureAspect
import one.ztd.workbench.web.api.InstanceRequestContextResolver
import one.ztd.workbench.web.api.RequestContextResolver
import one.ztd.workbench.web.api.TenantRequestContextResolver
import one.ztd.workbench.web.support.ContextWebMvcSupport
import one.ztd.workbench.web.support.ProjectWebMvcSupport
import one.ztd.workbench.web.support.TenantWebMvcFixtures
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
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
    mockMvc.perform(get("/api/admin/instance-admins")).andExpect(status().isUnauthorized())
  }

  @Test
  fun `grant tenant admin returns created admin for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          post("/api/manage/tenant-admins")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"userId":"usr_01JABCDEFGHJKMNPQRSTVWXYZ2"}""")
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.scope").value("tenant"))
  }

  @Test
  fun `revoke admin returns no content for authenticated instance user`() {
    val result =
      mockMvc
        .perform(
          delete("/api/admin/instance-admins/adm_01JABCDEFGHJKMNPQRSTVWXYZ0")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, ADMIN_SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent())
  }

  @Test
  fun `create grant returns created grant for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          post("/api/manage/grants")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "scope": "TENANT",
                "userId": "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
                "action": "project.write",
                "resourcePattern": "project:*",
                "effect": "ALLOW"
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
      .andExpect(jsonPath("$.action").value("project.write"))
  }

  @Test
  fun `expire grant returns no content for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          delete("/api/manage/grants/grt_01JABCDEFGHJKMNPQRSTVWXYZ0")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent())
  }

  @Test
  fun `list actions returns actions for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          get("/api/manage/actions")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].code").value("tenant.read"))
      .andExpect(jsonPath("$[0].resourcePattern").value("tenant:*"))
  }

  @Test
  fun `manage actions does not allow creating custom capabilities`() {
    mockMvc
      .perform(
        post("/api/manage/actions")
          .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
          .contentType(MediaType.APPLICATION_JSON)
          .content("""{"code":"project.archive","description":"Archive projects"}""")
      )
      .andExpect(status().isMethodNotAllowed())
  }

  @Test
  fun `list instance admins returns admins for authenticated instance user`() {
    val result =
      mockMvc
        .perform(
          get("/api/admin/instance-admins")
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
          get("/api/manage/tenant-admins")
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
          get("/api/manage/grants")
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
          post("/api/admin/instance-admins")
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
    fun sessionAuthenticator(): one.ztd.workbench.identity.auth.SessionAuthenticator =
      object : one.ztd.workbench.identity.auth.SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String): AuthenticatedPrincipal? =
          if (sessionId == ADMIN_SESSION || sessionId == TenantWebMvcFixtures.SESSION) {
            TenantWebMvcFixtures.PRINCIPAL
          } else {
            null
          }
      }

    @Bean
    fun bearerTokenAuthenticator(): one.ztd.workbench.identity.auth.BearerTokenAuthenticator =
      object : one.ztd.workbench.identity.auth.BearerTokenAuthenticator {
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
          request: one.ztd.workbench.identity.permission.model.AuthorizationRequest
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
      coEvery {
        service.grantTenantAdmin(
          tenantId = TenantWebMvcFixtures.TENANT_ID,
          userPublicId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ2",
          actorUserId = TenantWebMvcFixtures.USER_ID,
        )
      } returns
        AdminUserView(
          id = "adm_01JABCDEFGHJKMNPQRSTVWXYZ3",
          userId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ2",
          scope = AdminScope.TENANT,
          tenantId = TenantWebMvcFixtures.TENANT_ID.toString(),
          status = "active",
          validFrom = java.time.OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          validTo = null,
        )
      coEvery { service.revokeInstanceAdmin("adm_01JABCDEFGHJKMNPQRSTVWXYZ0") } returns true
      return true
    }

    @Bean
    fun permissionActionServiceSetup(service: PermissionActionService): Boolean {
      coEvery { service.listTenantCapabilities() } returns
        listOf(
          one.ztd.workbench.application.permission.TenantPermissionCapability(
            action = "tenant.read",
            resourcePattern = "tenant:*",
            name = "View tenant settings",
            description = "View tenant metadata and management information.",
          )
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
          one.ztd.workbench.application.permission.AccessGrantView(
            id = "grt_01JABCDEFGHJKMNPQRSTVWXYZ0",
            scope = one.ztd.workbench.identity.permission.GrantScope.TENANT,
            tenantId = TenantWebMvcFixtures.TENANT_ID.toString(),
            projectId = null,
            userId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
            action = "project.read",
            resourcePattern = "project:*",
            effect = one.ztd.workbench.identity.permission.model.PermissionEffect.ALLOW,
            validFrom = java.time.OffsetDateTime.parse("2026-07-04T00:00:00Z"),
            validTo = null,
          )
        )
      coEvery {
        service.createGrant(
          CreateManagedAccessGrantCommand(
            scope = one.ztd.workbench.identity.permission.GrantScope.TENANT,
            tenantId = TenantWebMvcFixtures.TENANT_ID,
            userPublicId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
            actionCode = "project.write",
            resourcePattern = "project:*",
            effect = one.ztd.workbench.identity.permission.model.PermissionEffect.ALLOW,
            projectPublicId = null,
            actorUserId = TenantWebMvcFixtures.USER_ID,
          )
        )
      } returns
        one.ztd.workbench.application.permission.AccessGrantView(
          id = "grt_01JABCDEFGHJKMNPQRSTVWXYZ1",
          scope = one.ztd.workbench.identity.permission.GrantScope.TENANT,
          tenantId = TenantWebMvcFixtures.TENANT_ID.toString(),
          projectId = null,
          userId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
          action = "project.write",
          resourcePattern = "project:*",
          effect = one.ztd.workbench.identity.permission.model.PermissionEffect.ALLOW,
          validFrom = java.time.OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          validTo = null,
        )
      coEvery { service.expireGrant("grt_01JABCDEFGHJKMNPQRSTVWXYZ0") } returns true
      return true
    }
  }

  private companion object {
    const val ADMIN_SESSION = "admin-session"
  }
}
