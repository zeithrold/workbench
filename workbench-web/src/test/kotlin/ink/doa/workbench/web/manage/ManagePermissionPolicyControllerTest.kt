package ink.doa.workbench.web.manage

import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
import ink.doa.workbench.security.identity.SessionService
import ink.doa.workbench.security.permission.PermissionPolicyManagementService
import ink.doa.workbench.security.permission.PermissionPolicyView
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
  TenantScopedWebMvcSupport::class,
  GlobalExceptionHandler::class,
  ManagePermissionPolicyControllerTest.TestBeans::class,
)
class ManagePermissionPolicyControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list permission policies rejects unauthenticated requests`() {
    mockMvc.perform(get("/api/manage/permission-policies")).andExpect(status().isUnauthorized())
  }

  @Test
  fun `list permission policies returns policies for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          get("/api/manage/permission-policies")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].code").value("admin"))
  }

  @Test
  fun `create permission policy returns created policy for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          post("/api/manage/permission-policies")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "code": "editor",
                "name": "Editor",
                "description": "Can edit issues"
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
      .andExpect(jsonPath("$.code").value("editor"))
  }

  @Test
  fun `add policy rule returns updated policy for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          post("/api/manage/permission-policies/pol_01JABCDEFGHJKMNPQRSTVWXYZ0/rules")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "action": "issue.update",
                "resourcePattern": "issue:*"
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
      .andExpect(jsonPath("$.rules[0].action").value("issue.update"))
  }

  @Test
  fun `add policy rule accepts optional condition`() {
    val result =
      mockMvc
        .perform(
          post("/api/manage/permission-policies/pol_01JABCDEFGHJKMNPQRSTVWXYZ0/rules")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "action": "issue.view",
                "resourcePattern": "issue:*",
                "condition": {"field":"statusGroup","op":"eq","value":"todo"}
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
      .andExpect(jsonPath("$.rules[0].condition.field").value("statusGroup"))
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
    fun permissionPolicyManagementService(): PermissionPolicyManagementService =
      mockk(relaxed = true)

    @Bean
    fun permissionPolicyManagementServiceSetup(
      service: PermissionPolicyManagementService
    ): Boolean {
      val samplePolicy =
        PermissionPolicyView(
          id = "pol_01JABCDEFGHJKMNPQRSTVWXYZ0",
          code = "admin",
          name = "Admin",
          description = "Full access",
          builtin = true,
          rules = emptyList(),
        )
      coEvery { service.listPolicies(TenantWebMvcFixtures.TENANT_ID) } returns listOf(samplePolicy)
      coEvery {
        service.createPolicy(
          tenantId = TenantWebMvcFixtures.TENANT_ID,
          code = "editor",
          name = "Editor",
          description = "Can edit issues",
        )
      } returns
        PermissionPolicyView(
          id = "pol_01JABCDEFGHJKMNPQRSTVWXYZ1",
          code = "editor",
          name = "Editor",
          description = "Can edit issues",
          builtin = false,
          rules = emptyList(),
        )
      coEvery {
        service.addPolicyRule(
          tenantId = TenantWebMvcFixtures.TENANT_ID,
          policyPublicId = "pol_01JABCDEFGHJKMNPQRSTVWXYZ0",
          action = "issue.update",
          resourcePattern = "issue:*",
          effect = ink.doa.workbench.core.permission.model.PermissionEffect.ALLOW,
          conditionJson = null,
        )
      } returns
        samplePolicy.copy(
          rules =
            listOf(
              ink.doa.workbench.security.permission.PermissionPolicyRuleView(
                action = "issue.update",
                resourcePattern = "issue:*",
                effect = "ALLOW",
              )
            )
        )
      coEvery {
        service.addPolicyRule(
          tenantId = TenantWebMvcFixtures.TENANT_ID,
          policyPublicId = "pol_01JABCDEFGHJKMNPQRSTVWXYZ0",
          action = "issue.view",
          resourcePattern = "issue:*",
          effect = ink.doa.workbench.core.permission.model.PermissionEffect.ALLOW,
          conditionJson = match { it != null && it.contains("statusGroup") },
        )
      } returns
        samplePolicy.copy(
          rules =
            listOf(
              ink.doa.workbench.security.permission.PermissionPolicyRuleView(
                action = "issue.view",
                resourcePattern = "issue:*",
                effect = "ALLOW",
                condition = """{"field":"statusGroup","op":"eq","value":"todo"}""",
              )
            )
        )
      return true
    }
  }
}
