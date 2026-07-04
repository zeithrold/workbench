package ink.doa.workbench.web.project

import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.project.ProjectRepository
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
import ink.doa.workbench.security.identity.SessionService
import ink.doa.workbench.service.project.ProjectManagementApplicationService
import ink.doa.workbench.service.project.ProjectView
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
  ink.doa.workbench.web.api.ProjectRequestContextResolver::class,
  ink.doa.workbench.web.support.ContextWebMvcSupport::class,
  ink.doa.workbench.web.support.ProjectWebMvcSupport::class,
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
            id = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
            displayName = "Ada",
            primaryEmail = "ada@example.test",
          ),
        archivedAt = null,
      )
  }
}
