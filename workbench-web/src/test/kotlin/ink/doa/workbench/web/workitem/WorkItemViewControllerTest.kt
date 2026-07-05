package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.project.ProjectResolver
import ink.doa.workbench.agile.workitem.WorkItemViewService
import ink.doa.workbench.agile.workitem.WorkItemViewView
import ink.doa.workbench.core.common.summary.ProjectSummary
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.workitem.view.WorkItemViewDefaults
import ink.doa.workbench.core.workitem.view.WorkItemViewVisibility
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
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import java.time.OffsetDateTime
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

@WebMvcTest(ProjectWorkItemViewController::class, TenantWorkItemViewController::class)
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
  WorkItemViewControllerTest.TestBeans::class,
)
class WorkItemViewControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list project views rejects unauthenticated requests`() {
    mockMvc
      .perform(get("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-item-views"))
      .andExpect(status().isUnauthorized())
  }

  @Test
  fun `create project view returns created view`() {
    val result =
      mockMvc
        .perform(
          post("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-item-views")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "name": "My backlog",
                "visibility": "private"
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
      .andExpect(jsonPath("$.id").value("wiv_test"))
      .andExpect(jsonPath("$.name").value("My backlog"))
      .andExpect(jsonPath("$.visibility").value("private"))
  }

  @Test
  fun `list tenant views returns views for authenticated user`() {
    val result =
      mockMvc
        .perform(
          get("/api/work-item-views")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].id").value("wiv_tenant"))
  }

  @Test
  fun `get project view returns view for authenticated user`() {
    val result =
      mockMvc
        .perform(
          get("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-item-views/wiv_test")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value("wiv_test"))
  }

  @Test
  fun `update project view returns updated view`() {
    val result =
      mockMvc
        .perform(
          patch("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-item-views/wiv_test")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Renamed"}""")
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name").value("Renamed"))
  }

  @Test
  fun `delete project view returns no content`() {
    val result =
      mockMvc
        .perform(
          delete("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-item-views/wiv_test")
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
    fun projectResolverSetup(resolver: ProjectResolver): Boolean {
      coEvery {
        resolver.resolveProject(
          TenantWebMvcFixtures.TENANT_ID,
          TenantWebMvcFixtures.PROJECT_PUBLIC_ID,
        )
      } returns TenantWebMvcFixtures.PROJECT_RECORD
      return true
    }

    @Bean fun viewService(): WorkItemViewService = mockk(relaxed = true)

    @Bean
    fun viewServiceSetup(service: WorkItemViewService): Boolean {
      val now = OffsetDateTime.parse("2026-01-01T00:00:00Z")
      val owner =
        UserSummary(
          id = TenantWebMvcFixtures.PRINCIPAL.user.apiId.value,
          displayName = "Ada",
          primaryEmail = null,
        )
      val sampleView =
        WorkItemViewView(
          id = "wiv_test",
          name = "My backlog",
          description = null,
          visibility = WorkItemViewVisibility.PRIVATE,
          owner = owner,
          project =
            ProjectSummary(
              id = TenantWebMvcFixtures.PROJECT_PUBLIC_ID,
              identifier = "WB",
              name = "Workbench",
            ),
          filter = WorkItemViewDefaults.EMPTY_FILTER,
          sort = WorkItemViewDefaults.EMPTY_SORT,
          group = WorkItemViewDefaults.EMPTY_GROUP,
          displayFields = WorkItemViewDefaults.EMPTY_DISPLAY_FIELDS,
          createdAt = now,
          updatedAt = now,
        )
      coEvery { service.create(any()) } returns sampleView
      coEvery {
        service.list(TenantWebMvcFixtures.TENANT_ID, TenantWebMvcFixtures.PROJECT_ID, any())
      } returns listOf(sampleView)
      coEvery { service.list(TenantWebMvcFixtures.TENANT_ID, null, any()) } returns
        listOf(
          sampleView.copy(
            id = "wiv_tenant",
            project = null,
            visibility = WorkItemViewVisibility.TENANT,
          )
        )
      coEvery {
        service.get(
          TenantWebMvcFixtures.TENANT_ID,
          TenantWebMvcFixtures.PROJECT_ID,
          "wiv_test",
          any(),
        )
      } returns sampleView
      coEvery { service.update(any()) } returns sampleView.copy(name = "Renamed")
      coEvery { service.delete(any()) } returns Unit
      return true
    }
  }
}
