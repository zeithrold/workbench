package ink.doa.workbench.web.manage

import ink.doa.workbench.application.permission.PermissionGroupManagementService
import ink.doa.workbench.application.permission.PermissionGroupView
import ink.doa.workbench.identity.SessionService
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
import ink.doa.workbench.web.api.GlobalExceptionHandler
import ink.doa.workbench.web.api.InfrastructureAspect
import ink.doa.workbench.web.api.RequestContextResolver
import ink.doa.workbench.web.api.TenantRequestContextResolver
import ink.doa.workbench.web.support.TenantScopedWebMvcSupport
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

@WebMvcTest(ManagePermissionGroupController::class)
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
  ManagePermissionGroupControllerTest.TestBeans::class,
)
class ManagePermissionGroupControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list groups rejects unauthenticated requests`() {
    mockMvc.perform(get("/api/manage/groups")).andExpect(status().isUnauthorized())
  }

  @Test
  fun `list groups returns groups for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          get("/api/manage/groups")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].code").value("developers"))
  }

  @Test
  fun `create group returns created group for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          post("/api/manage/groups")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "code": "qa",
                "name": "QA",
                "description": "Quality assurance"
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
      .andExpect(jsonPath("$.code").value("qa"))
  }

  @TestConfiguration
  class TestBeans {
    @Bean
    fun sessionAuthenticator(): ink.doa.workbench.identity.auth.SessionAuthenticator =
      object : ink.doa.workbench.identity.auth.SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String) =
          if (sessionId == TenantWebMvcFixtures.SESSION) TenantWebMvcFixtures.PRINCIPAL else null
      }

    @Bean
    fun bearerTokenAuthenticator(): ink.doa.workbench.identity.auth.BearerTokenAuthenticator =
      object : ink.doa.workbench.identity.auth.BearerTokenAuthenticator {
        override suspend fun authenticateBearerToken(token: String) = null
      }

    @Bean
    fun sessionService(): SessionService = mockk {
      coEvery { requireActiveTenant(any()) } returns TenantWebMvcFixtures.TENANT_RECORD
    }

    @Bean
    fun permissionGroupManagementService(): PermissionGroupManagementService = mockk(relaxed = true)

    @Bean
    fun permissionGroupManagementServiceSetup(service: PermissionGroupManagementService): Boolean {
      coEvery { service.listGroups(TenantWebMvcFixtures.TENANT_ID) } returns
        listOf(
          PermissionGroupView(
            id = "grp_01JABCDEFGHJKMNPQRSTVWXYZ0",
            code = "developers",
            name = "Developers",
            description = "Engineering",
            builtin = false,
          )
        )
      coEvery {
        service.createGroup(
          tenantId = TenantWebMvcFixtures.TENANT_ID,
          code = "qa",
          name = "QA",
          description = "Quality assurance",
        )
      } returns
        PermissionGroupView(
          id = "grp_01JABCDEFGHJKMNPQRSTVWXYZ1",
          code = "qa",
          name = "QA",
          description = "Quality assurance",
          builtin = false,
        )
      return true
    }
  }
}
