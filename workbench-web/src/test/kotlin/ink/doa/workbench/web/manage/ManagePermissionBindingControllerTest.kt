package ink.doa.workbench.web.manage

import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.permission.PermissionPrincipalType
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
import ink.doa.workbench.security.identity.SessionService
import ink.doa.workbench.security.permission.PermissionBindingManagementService
import ink.doa.workbench.security.permission.PermissionBindingView
import ink.doa.workbench.security.permission.PermissionPolicySummary
import ink.doa.workbench.web.api.GlobalExceptionHandler
import ink.doa.workbench.web.api.InfrastructureAspect
import ink.doa.workbench.web.api.RequestContextResolver
import ink.doa.workbench.web.api.TenantRequestContextResolver
import ink.doa.workbench.web.support.TenantScopedWebMvcSupport
import ink.doa.workbench.web.support.TenantWebMvcFixtures
import io.mockk.coEvery
import io.mockk.coJustRun
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ManagePermissionBindingController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  AopAutoConfiguration::class,
  InfrastructureAspect::class,
  RequestContextResolver::class,
  TenantRequestContextResolver::class,
  ink.doa.workbench.web.support.ContextWebMvcSupport::class,
  ink.doa.workbench.web.support.ProjectWebMvcSupport::class,
  TenantScopedWebMvcSupport::class,
  GlobalExceptionHandler::class,
  ManagePermissionBindingControllerTest.TestBeans::class,
)
class ManagePermissionBindingControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list bindings rejects unauthenticated requests`() {
    mockMvc.perform(get("/api/manage/permission-bindings")).andExpect(status().isUnauthorized())
  }

  @Test
  fun `list bindings returns bindings for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          get("/api/manage/permission-bindings")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].policy.code").value("member"))
  }

  @Test
  fun `create binding returns created binding for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          post("/api/manage/permission-bindings")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "principalType": "USER",
                "userId": "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
                "policyId": "pol_01JABCDEFGHJKMNPQRSTVWXYZ0"
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
      .andExpect(jsonPath("$.principalType").value("USER"))
  }

  @Test
  fun `expire binding returns no content for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          delete("/api/manage/permission-bindings/bnd_01JABCDEFGHJKMNPQRSTVWXYZ0")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent())
  }

  @TestConfiguration
  class TestBeans {
    @Bean
    fun sessionAuthenticator(): ink.doa.workbench.core.identity.auth.SessionAuthenticator =
      object : ink.doa.workbench.core.identity.auth.SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String) =
          if (sessionId == TenantWebMvcFixtures.SESSION) TenantWebMvcFixtures.PRINCIPAL else null
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

    @Bean
    fun permissionBindingManagementService(): PermissionBindingManagementService =
      mockk(relaxed = true)

    @Bean
    fun permissionBindingManagementServiceSetup(
      service: PermissionBindingManagementService
    ): Boolean {
      val sampleBinding =
        PermissionBindingView(
          id = "bnd_01JABCDEFGHJKMNPQRSTVWXYZ0",
          principalType = PermissionPrincipalType.USER.name,
          user =
            UserSummary(
              id = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
              displayName = "Ada",
              primaryEmail = "ada@example.test",
            ),
          group = null,
          policy =
            PermissionPolicySummary(
              id = "pol_01JABCDEFGHJKMNPQRSTVWXYZ0",
              code = "member",
              name = "Member",
            ),
          project = null,
        )
      coEvery { service.listBindings(TenantWebMvcFixtures.TENANT_ID) } returns listOf(sampleBinding)
      coEvery { service.createBinding(any()) } returns sampleBinding
      coJustRun {
        service.expireBinding(TenantWebMvcFixtures.TENANT_ID, "bnd_01JABCDEFGHJKMNPQRSTVWXYZ0")
      }
      return true
    }
  }
}
