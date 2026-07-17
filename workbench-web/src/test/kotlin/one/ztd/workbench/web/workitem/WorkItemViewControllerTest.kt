package one.ztd.workbench.web.workitem

import io.mockk.coEvery
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import java.time.OffsetDateTime
import one.ztd.workbench.agile.project.ProjectResolver
import one.ztd.workbench.agile.project.ProjectSummary
import one.ztd.workbench.agile.workitem.WorkItemViewService
import one.ztd.workbench.agile.workitem.WorkItemViewView
import one.ztd.workbench.agile.workitem.view.WorkItemViewDefaults
import one.ztd.workbench.agile.workitem.view.WorkItemViewVisibility
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.security.SecurityConfiguration
import one.ztd.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import one.ztd.workbench.security.WorkbenchAuthenticationFilter
import one.ztd.workbench.web.api.GlobalExceptionHandler
import one.ztd.workbench.web.api.InfrastructureAspect
import one.ztd.workbench.web.api.ProjectRequestContextResolver
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
  one.ztd.workbench.web.support.ContextWebMvcSupport::class,
  one.ztd.workbench.web.support.ProjectWebMvcSupport::class,
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

  @Test
  fun `create tenant view returns created view`() {
    val result =
      mockMvc
        .perform(
          post("/api/work-item-views")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "name": "Tenant board",
                "visibility": "tenant"
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
  }

  @Test
  fun `get tenant view returns view for authenticated user`() {
    val result =
      mockMvc
        .perform(
          get("/api/work-item-views/wiv_tenant")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value("wiv_tenant"))
  }

  @Test
  fun `update tenant view returns updated view`() {
    val result =
      mockMvc
        .perform(
          patch("/api/work-item-views/wiv_tenant")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Tenant renamed"}""")
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name").value("Renamed"))
  }

  @Test
  fun `delete tenant view returns no content`() {
    val result =
      mockMvc
        .perform(
          delete("/api/work-item-views/wiv_tenant")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent())
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
          id = TenantWebMvcFixtures.PRINCIPAL.user.apiId,
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
              id = PublicId(TenantWebMvcFixtures.PROJECT_PUBLIC_ID),
              identifier = "WB",
              name = "Workbench",
            ),
          query = WorkItemViewDefaults.EMPTY_QUERY,
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
      coEvery {
        service.get(TenantWebMvcFixtures.TENANT_ID, null, "wiv_tenant", any())
      } returns
        sampleView.copy(
          id = "wiv_tenant",
          project = null,
          visibility = WorkItemViewVisibility.TENANT,
        )
      coEvery { service.update(any()) } returns sampleView.copy(name = "Renamed")
      coEvery { service.delete(any()) } returns Unit
      return true
    }
  }
}
