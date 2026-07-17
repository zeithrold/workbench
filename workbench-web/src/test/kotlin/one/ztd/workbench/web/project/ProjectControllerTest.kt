package one.ztd.workbench.web.project

import io.mockk.coEvery
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import one.ztd.workbench.agile.project.ProjectRepository
import one.ztd.workbench.application.project.ProjectManagementApplicationService
import one.ztd.workbench.application.project.ProjectView
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.identity.permission.model.AuthorizationDecision
import one.ztd.workbench.identity.permission.model.DecisionReason
import one.ztd.workbench.identity.permission.model.PermissionService
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.security.SecurityConfiguration
import one.ztd.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import one.ztd.workbench.security.WorkbenchAuthenticationFilter
import one.ztd.workbench.web.api.GlobalExceptionHandler
import one.ztd.workbench.web.api.InfrastructureAspect
import one.ztd.workbench.web.api.RequestContextResolver
import one.ztd.workbench.web.api.TenantRequestContextResolver
import one.ztd.workbench.web.support.TenantScopedWebMvcSupport
import one.ztd.workbench.web.support.TenantWebMvcFixtures
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ProjectController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  AopAutoConfiguration::class,
  InfrastructureAspect::class,
  RequestContextResolver::class,
  TenantRequestContextResolver::class,
  one.ztd.workbench.web.api.ProjectRequestContextResolver::class,
  one.ztd.workbench.web.support.ContextWebMvcSupport::class,
  one.ztd.workbench.web.support.ProjectWebMvcSupport::class,
  TenantScopedWebMvcSupport::class,
  GlobalExceptionHandler::class,
  ProjectControllerTest.TestBeans::class,
)
class ProjectControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list projects rejects unauthenticated requests`() {
    mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized())
  }

  @Test
  fun `list projects returns visible projects for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          get("/api/projects")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].identifier").value("CORE"))
      .andExpect(jsonPath("$[0].name").value("Core Platform"))
  }

  @Test
  fun `create project returns created project for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          post("/api/projects")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "identifier": "NEW",
                "name": "New Project",
                "description": "Created from test"
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
      .andExpect(jsonPath("$.identifier").value("NEW"))
  }

  @Test
  fun `get project returns project for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          get("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.identifier").value("CORE"))
  }

  @Test
  fun `update project returns updated project for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          patch("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Updated Platform"}""")
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name").value("Updated Platform"))
  }

  @Test
  fun `archive project returns archived project for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          post("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/archive")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("archived"))
  }

  @Test
  fun `unarchive project returns active project for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          post("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/unarchive")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("active"))
  }

  @Test
  fun `delete project returns accepted project for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          delete("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isAccepted())
      .andExpect(jsonPath("$.status").value("destroying"))
  }

  @TestConfiguration
  class TestBeans {
    @Bean
    fun sessionAuthenticator(): one.ztd.workbench.identity.auth.SessionAuthenticator =
      object : one.ztd.workbench.identity.auth.SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String) =
          if (sessionId == TenantWebMvcFixtures.SESSION) TenantWebMvcFixtures.PRINCIPAL else null
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

    @Bean
    @Primary
    fun projectPermissionService(): PermissionService =
      object : PermissionService {
        override suspend fun decide(
          request: one.ztd.workbench.identity.permission.model.AuthorizationRequest
        ): AuthorizationDecision =
          if (request.action.code == "project.read" && request.resource.projectId == null) {
            AuthorizationDecision.Deny(DecisionReason("missing", "denied"))
          } else {
            AuthorizationDecision.Allow(DecisionReason("grant", "allowed"))
          }
      }

    @Bean
    fun projectResolverSetup(resolver: one.ztd.workbench.agile.project.ProjectResolver): Boolean {
      coEvery {
        resolver.resolveProject(
          TenantWebMvcFixtures.TENANT_ID,
          TenantWebMvcFixtures.PROJECT_PUBLIC_ID,
        )
      } returns TenantWebMvcFixtures.PROJECT_RECORD
      return true
    }

    @Bean
    fun projectRepositorySetup(repository: ProjectRepository): Boolean {
      coEvery {
        repository.findById(TenantWebMvcFixtures.TENANT_ID, TenantWebMvcFixtures.PROJECT_ID)
      } returns TenantWebMvcFixtures.PROJECT_RECORD
      return true
    }

    @Bean
    fun projectManagementServiceSetup(service: ProjectManagementApplicationService): Boolean {
      coEvery {
        service.list(TenantWebMvcFixtures.TENANT_ID, TenantWebMvcFixtures.USER_ID, null)
      } returns listOf(SAMPLE_PROJECT)
      coEvery { service.create(any(), TenantWebMvcFixtures.USER_ID) } returns
        SAMPLE_PROJECT.copy(
          identifier = "NEW",
          name = "New Project",
          description = "Created from test",
        )
      coEvery {
        service.get(
          TenantWebMvcFixtures.TENANT_ID,
          TenantWebMvcFixtures.USER_ID,
          TenantWebMvcFixtures.PROJECT_PUBLIC_ID,
        )
      } returns SAMPLE_PROJECT
      coEvery { service.update(any()) } returns SAMPLE_PROJECT.copy(name = "Updated Platform")
      coEvery {
        service.archive(
          TenantWebMvcFixtures.TENANT_ID,
          TenantWebMvcFixtures.PROJECT_ID,
          TenantWebMvcFixtures.USER_ID,
        )
      } returns SAMPLE_PROJECT.copy(status = "archived")
      coEvery {
        service.unarchive(TenantWebMvcFixtures.TENANT_ID, TenantWebMvcFixtures.PROJECT_ID)
      } returns SAMPLE_PROJECT
      coEvery {
        service.requestDestroy(
          tenantId = TenantWebMvcFixtures.TENANT_ID,
          tenantPublicId = TenantWebMvcFixtures.TENANT_RECORD.apiId,
          projectPublicId = TenantWebMvcFixtures.PROJECT_PUBLIC_ID,
          actorUserId = TenantWebMvcFixtures.USER_ID,
          deleteReason = null,
        )
      } returns SAMPLE_PROJECT.copy(status = "destroying")
      return true
    }
  }

  private companion object {
    val SAMPLE_PROJECT =
      ProjectView(
        id = "prj_01JABCDEFGHJKMNPQRSTVWXYZ0",
        identifier = "CORE",
        name = "Core Platform",
        description = "Platform engineering",
        status = "active",
        nonMemberVisibility = "invisible",
        nonMemberJoinPolicy = "admin_only",
        lead =
          UserSummary(
            id = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
            displayName = "Ada",
            primaryEmail = "ada@example.test",
          ),
        archivedAt = null,
      )
  }
}
