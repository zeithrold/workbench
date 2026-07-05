package ink.doa.workbench.web.project

import ink.doa.workbench.agile.project.ProjectMemberPolicyView
import ink.doa.workbench.agile.project.ProjectMemberService
import ink.doa.workbench.agile.project.ProjectPermissionPolicySummary
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
import ink.doa.workbench.security.identity.SessionService
import ink.doa.workbench.web.api.GlobalExceptionHandler
import ink.doa.workbench.web.api.InfrastructureAspect
import ink.doa.workbench.web.api.ProjectRequestContextResolver
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

@WebMvcTest(ProjectMemberController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  AopAutoConfiguration::class,
  InfrastructureAspect::class,
  RequestContextResolver::class,
  TenantRequestContextResolver::class,
  ProjectRequestContextResolver::class,
  ink.doa.workbench.web.support.ContextWebMvcSupport::class,
  ink.doa.workbench.web.support.ProjectWebMvcSupport::class,
  TenantScopedWebMvcSupport::class,
  GlobalExceptionHandler::class,
  ProjectMemberControllerTest.TestBeans::class,
)
class ProjectMemberControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list members rejects unauthenticated requests`() {
    mockMvc
      .perform(get("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/members"))
      .andExpect(status().isUnauthorized())
  }

  @Test
  fun `list members returns project members for authenticated user`() {
    val result =
      mockMvc
        .perform(
          get("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/members")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].user.displayName").value("Ada"))
  }

  @Test
  fun `add member returns created member for authenticated user`() {
    val result =
      mockMvc
        .perform(
          post("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/members")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"userId":"usr_01JABCDEFGHJKMNPQRSTVWXYZ1"}""")
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.user.displayName").value("Ada"))
  }

  @Test
  fun `attach policy returns member with policy for authenticated user`() {
    val result =
      mockMvc
        .perform(
          post(
              "/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/members/usr_01JABCDEFGHJKMNPQRSTVWXYZ1/policies"
            )
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"policyId":"pol_01JABCDEFGHJKMNPQRSTVWXYZ0"}""")
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.policies[0].policy.code").value("member"))
  }

  @Test
  fun `remove policy returns no content for authenticated user`() {
    val result =
      mockMvc
        .perform(
          delete(
              "/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/members/" +
                "usr_01JABCDEFGHJKMNPQRSTVWXYZ1/policies/bnd_01JABCDEFGHJKMNPQRSTVWXYZ0"
            )
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent())
  }

  @Test
  fun `join project returns member for authenticated user`() {
    val result =
      mockMvc
        .perform(
          post("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/join")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.user.displayName").value("Ada"))
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
    fun projectResolverSetup(resolver: ink.doa.workbench.agile.project.ProjectResolver): Boolean {
      coEvery {
        resolver.resolveProject(
          TenantWebMvcFixtures.TENANT_ID,
          TenantWebMvcFixtures.PROJECT_PUBLIC_ID,
        )
      } returns TenantWebMvcFixtures.PROJECT_RECORD
      return true
    }

    @Bean
    fun projectMemberServiceSetup(service: ProjectMemberService): Boolean {
      val memberView =
        ink.doa.workbench.agile.project.ProjectMemberView(
          user =
            UserSummary(
              id = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
              displayName = "Ada",
              primaryEmail = "ada@example.test",
            ),
          policies =
            listOf(
              ProjectMemberPolicyView(
                bindingId = "bnd_01JABCDEFGHJKMNPQRSTVWXYZ0",
                policy =
                  ProjectPermissionPolicySummary(
                    id = "pol_01JABCDEFGHJKMNPQRSTVWXYZ0",
                    code = "member",
                    name = "Member",
                  ),
              )
            ),
        )
      coEvery {
        service.listMembers(TenantWebMvcFixtures.TENANT_ID, TenantWebMvcFixtures.PROJECT_ID)
      } returns listOf(memberView.copy(policies = emptyList()))
      coEvery { service.addMember(any()) } returns memberView.copy(policies = emptyList())
      coEvery { service.attachPolicy(any()) } returns memberView
      coJustRun {
        service.removePolicy(TenantWebMvcFixtures.TENANT_ID, "bnd_01JABCDEFGHJKMNPQRSTVWXYZ0")
      }
      coEvery {
        service.join(
          tenantId = TenantWebMvcFixtures.TENANT_ID,
          projectId = TenantWebMvcFixtures.PROJECT_ID,
          userId = TenantWebMvcFixtures.USER_ID,
          actorUserId = TenantWebMvcFixtures.USER_ID,
        )
      } returns memberView.copy(policies = emptyList())
      return true
    }
  }
}
