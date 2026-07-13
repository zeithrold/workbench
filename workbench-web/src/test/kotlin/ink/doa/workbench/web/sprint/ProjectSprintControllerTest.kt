package ink.doa.workbench.web.sprint

import ink.doa.workbench.agile.project.ProjectResolver
import ink.doa.workbench.agile.sprint.SprintCloseOperationView
import ink.doa.workbench.agile.sprint.SprintService
import ink.doa.workbench.agile.sprint.SprintView
import ink.doa.workbench.agile.sprint.model.SprintStatus
import ink.doa.workbench.identity.SessionService
import ink.doa.workbench.identity.common.summary.UserSummary
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
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

@WebMvcTest(ProjectSprintController::class)
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
  ProjectSprintControllerTest.TestBeans::class,
)
class ProjectSprintControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list sprints rejects unauthenticated requests`() {
    mockMvc
      .perform(get("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/sprints"))
      .andExpect(status().isUnauthorized())
  }

  @Test
  fun `list sprints returns sprints for authenticated user`() {
    val result =
      mockMvc
        .perform(
          get("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/sprints?status=planned")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].id").value("spr_test"))
  }

  @Test
  fun `get sprint returns sprint for authenticated user`() {
    val result =
      mockMvc
        .perform(
          get("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/sprints/spr_test")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value("spr_test"))
  }

  @Test
  fun `update sprint returns updated sprint`() {
    val result =
      mockMvc
        .perform(
          patch("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/sprints/spr_test")
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
  fun `start sprint returns active sprint`() {
    val result =
      mockMvc
        .perform(
          post("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/sprints/spr_test/start")
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
  fun `close sprint queues close operation`() {
    val result =
      mockMvc
        .perform(
          post("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/sprints/spr_test/close")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .header("Idempotency-Key", "close-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"disposition":"KEEP"}""")
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isAccepted())
      .andExpect(jsonPath("$.status").value("QUEUED"))
  }

  @Test
  fun `archive sprint returns sprint`() {
    val result =
      mockMvc
        .perform(
          post("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/sprints/spr_test/archive")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value("spr_test"))
  }

  @Test
  fun `delete sprint returns no content`() {
    val result =
      mockMvc
        .perform(
          delete("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/sprints/spr_test")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"deleteReason":"duplicate"}""")
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent())
  }

  @Test
  fun `create sprint returns created sprint`() {
    val result =
      mockMvc
        .perform(
          post("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/sprints")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "name": "Sprint 1",
                "goal": "Ship"
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
      .andExpect(jsonPath("$.id").value("spr_test"))
      .andExpect(jsonPath("$.status").value("planned"))
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
    fun projectResolverSetup(resolver: ProjectResolver): Boolean {
      coEvery {
        resolver.resolveProject(
          TenantWebMvcFixtures.TENANT_ID,
          TenantWebMvcFixtures.PROJECT_PUBLIC_ID,
        )
      } returns TenantWebMvcFixtures.PROJECT_RECORD
      return true
    }

    @Bean fun sprintService(): SprintService = mockk(relaxed = true)

    @Bean
    fun sprintServiceSetup(service: SprintService): Boolean {
      val now = OffsetDateTime.parse("2026-01-01T00:00:00Z")
      val sampleView =
        SprintView(
          id = "spr_test",
          name = "Sprint 1",
          goal = "Ship",
          status = SprintStatus.PLANNED,
          startAt = null,
          endAt = null,
          closedAt = null,
          createdBy =
            UserSummary(
              id = TenantWebMvcFixtures.PRINCIPAL.user.apiId,
              displayName = "Ada",
              primaryEmail = null,
            ),
          createdAt = now,
          updatedAt = now,
        )
      coEvery { service.create(any()) } returns sampleView
      coEvery {
        service.list(TenantWebMvcFixtures.TENANT_ID, TenantWebMvcFixtures.PROJECT_ID, any())
      } returns listOf(sampleView)
      coEvery {
        service.get(TenantWebMvcFixtures.TENANT_ID, TenantWebMvcFixtures.PROJECT_ID, "spr_test")
      } returns sampleView
      coEvery { service.update(any()) } returns sampleView.copy(name = "Renamed")
      coEvery { service.start(any()) } returns sampleView.copy(status = SprintStatus.ACTIVE)
      coEvery {
        service.close(any())
      } returns
        SprintCloseOperationView(
          id = "sop_test",
          sprintId = "spr_test",
          targetSprintId = null,
          disposition = "KEEP",
          status = "QUEUED",
          totalItems = 0,
          processedItems = 0,
          failedItems = 0,
          lastError = null,
          createdAt = now,
          startedAt = null,
          completedAt = null,
        )
      coEvery { service.archive(any()) } returns sampleView
      coEvery { service.delete(any()) } returns Unit
      return true
    }
  }
}
