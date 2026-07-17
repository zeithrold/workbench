package one.ztd.workbench.web.instance

import io.mockk.coEvery
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.application.identity.PublicIdResolver
import one.ztd.workbench.application.instance.CreateTenantView
import one.ztd.workbench.application.instance.TenantManagementApplicationService
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.identity.permission.model.AuthorizationDecision
import one.ztd.workbench.identity.permission.model.AuthorizationScope
import one.ztd.workbench.identity.permission.model.DecisionReason
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.security.SecurityConfiguration
import one.ztd.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import one.ztd.workbench.security.WorkbenchAuthenticationFilter
import one.ztd.workbench.tenant.model.TenantRecord
import one.ztd.workbench.tenant.tenant.TenantOperationalGuard
import one.ztd.workbench.web.api.GlobalExceptionHandler
import one.ztd.workbench.web.api.InfrastructureAspect
import one.ztd.workbench.web.api.InstanceRequestContextResolver
import one.ztd.workbench.web.api.RequestContextResolver
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(TenantAdminController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  AopAutoConfiguration::class,
  InfrastructureAspect::class,
  RequestContextResolver::class,
  InstanceRequestContextResolver::class,
  one.ztd.workbench.web.support.ContextWebMvcSupport::class,
  one.ztd.workbench.web.support.ProjectWebMvcSupport::class,
  GlobalExceptionHandler::class,
  TenantAdminControllerTest.TestBeans::class,
)
class TenantAdminControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list tenants returns all tenants for instance administrator`() {
    val result =
      mockMvc
        .perform(
          get("/api/admin/tenants").cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, ADMIN_SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].slug").value("acme"))
      .andExpect(jsonPath("$[0].status").value("ACTIVE"))
  }

  @Test
  fun `list tenants filters by slug for instance administrator`() {
    val result =
      mockMvc
        .perform(
          get("/api/admin/tenants")
            .param("slug", "acme")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, ADMIN_SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].slug").value("acme"))
  }

  @Test
  fun `create tenant returns created tenant for instance administrator`() {
    val result =
      mockMvc
        .perform(
          post("/api/admin/tenants")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, ADMIN_SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "name": "Acme",
                "slug": "acme-new",
                "timezone": "UTC",
                "locale": "en-US",
                "adminAssignment": {
                  "mode": "SELF"
                }
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
      .andExpect(header().string("Location", "/api/admin/tenants/${SAMPLE_TENANT.apiId.value}"))
      .andExpect(jsonPath("$.name").value("Acme"))
      .andExpect(jsonPath("$.slug").value("acme"))
  }

  @Test
  fun `get tenant returns tenant for instance administrator`() {
    val result =
      mockMvc
        .perform(
          get("/api/admin/tenants/${SAMPLE_TENANT.apiId.value}")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, ADMIN_SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(SAMPLE_TENANT.apiId.value))
      .andExpect(jsonPath("$.name").value("Acme"))
  }

  @Test
  fun `patch tenant returns updated tenant for instance administrator`() {
    val result =
      mockMvc
        .perform(
          patch("/api/admin/tenants/${SAMPLE_TENANT.apiId.value}")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, ADMIN_SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "name": "Acme Corp",
                "timezone": "America/New_York"
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
      .andExpect(jsonPath("$.name").value("Acme Updated"))
      .andExpect(jsonPath("$.timezone").value("America/New_York"))
  }

  @Test
  fun `destroy tenant accepts destruction without request body`() {
    val result =
      mockMvc
        .perform(
          delete("/api/admin/tenants/${SAMPLE_TENANT.apiId.value}")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, ADMIN_SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isAccepted())
  }

  @Test
  fun `destroy tenant accepts destruction for instance administrator`() {
    val result =
      mockMvc
        .perform(
          delete("/api/admin/tenants/${SAMPLE_TENANT.apiId.value}")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, ADMIN_SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"deleteReason": "cleanup"}""")
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isAccepted())
  }

  @TestConfiguration
  class TestBeans {
    @Bean
    fun sessionAuthenticator(): one.ztd.workbench.identity.auth.SessionAuthenticator =
      object : one.ztd.workbench.identity.auth.SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String): AuthenticatedPrincipal? =
          if (sessionId == ADMIN_SESSION) ADMIN_PRINCIPAL else null
      }

    @Bean
    fun bearerTokenAuthenticator(): one.ztd.workbench.identity.auth.BearerTokenAuthenticator =
      object : one.ztd.workbench.identity.auth.BearerTokenAuthenticator {
        override suspend fun authenticateBearerToken(token: String): AuthenticatedPrincipal? = null
      }

    @Bean
    fun permissionService(): one.ztd.workbench.identity.permission.model.PermissionService =
      object : one.ztd.workbench.identity.permission.model.PermissionService {
        override suspend fun decide(
          request: one.ztd.workbench.identity.permission.model.AuthorizationRequest
        ): one.ztd.workbench.identity.permission.model.AuthorizationDecision =
          if (
            request.scope == AuthorizationScope.INSTANCE && request.subject.userId == ADMIN_USER_ID
          ) {
            AuthorizationDecision.Allow(DecisionReason("grant_allowed", "allowed"))
          } else {
            AuthorizationDecision.Deny(DecisionReason("grant_denied", "denied"))
          }
      }

    @Bean
    fun clock(): java.time.Clock =
      java.time.Clock.fixed(java.time.Instant.parse("2026-07-03T00:00:00Z"), ZoneOffset.UTC)

    @Bean fun tenantOperationalGuard(): TenantOperationalGuard = mockk(relaxed = true)

    @Bean fun sessionService(): SessionService = mockk(relaxed = true)

    @Bean
    fun publicIdResolver(): PublicIdResolver = mockk {
      coEvery { resolveUser(any()) } returns
        UserRecord(
          id = ADMIN_USER_ID,
          apiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
          displayName = "Admin",
          primaryEmail = "admin@example.test",
        )
    }

    @Bean
    fun tenantManagementService(): TenantManagementApplicationService {
      val service = mockk<TenantManagementApplicationService>()
      val updatedTenant = SAMPLE_TENANT.copy(name = "Acme Updated", timezone = "America/New_York")
      coEvery { service.list(null) } returns listOf(SAMPLE_TENANT)
      coEvery { service.list("acme") } returns listOf(SAMPLE_TENANT)
      coEvery { service.create(any()) } returns SAMPLE_TENANT
      coEvery { service.createWithAdmin(any(), any(), any()) } returns
        CreateTenantView(tenant = SAMPLE_TENANT, admin = null, invitationLink = null)
      coEvery { service.get(SAMPLE_TENANT.apiId.value) } returns SAMPLE_TENANT
      coEvery {
        service.update(
          tenantPublicId = SAMPLE_TENANT.apiId.value,
          name = "Acme Corp",
          slug = null,
          timezone = "America/New_York",
          locale = null,
        )
      } returns updatedTenant
      coEvery {
        service.requestDestroy(
          tenantPublicId = SAMPLE_TENANT.apiId.value,
          actorUserId = ADMIN_USER_ID,
          deleteReason = null,
        )
      } returns SAMPLE_TENANT
      coEvery {
        service.requestDestroy(
          tenantPublicId = SAMPLE_TENANT.apiId.value,
          actorUserId = ADMIN_USER_ID,
          deleteReason = "cleanup",
        )
      } returns SAMPLE_TENANT
      return service
    }
  }

  private companion object {
    const val ADMIN_SESSION = "admin-session"
    val ADMIN_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val SAMPLE_TENANT =
      TenantRecord(
        id = UUID.fromString("00000000-0000-0000-0000-000000000010"),
        apiId = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        slug = "acme",
        name = "Acme",
        timezone = "UTC",
        locale = "en-US",
        createdAt = OffsetDateTime.parse("2026-07-03T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-03T00:00:00Z"),
      )
    val ADMIN_PRINCIPAL =
      AuthenticatedPrincipal(
        user =
          UserRecord(
            id = ADMIN_USER_ID,
            apiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
            displayName = "Admin",
            primaryEmail = "admin@example.test",
          ),
        loginAccountId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        sessionId = "session-id",
        bearerTokenId = null,
      )
  }
}
