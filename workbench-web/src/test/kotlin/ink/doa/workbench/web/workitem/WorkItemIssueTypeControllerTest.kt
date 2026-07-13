package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.project.ProjectService
import ink.doa.workbench.agile.workitem.WorkItemCatalogService
import ink.doa.workbench.agile.workitem.model.IssueTypeRecord
import ink.doa.workbench.agile.workitem.model.WorkItemConfigScope
import ink.doa.workbench.identity.SessionService
import ink.doa.workbench.kernel.common.ids.PublicId
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(WorkItemIssueTypeController::class)
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
  WorkItemIssueTypeControllerTest.TestBeans::class,
)
class WorkItemIssueTypeControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list issue types rejects unauthenticated requests`() {
    mockMvc.perform(get("/api/work-item-catalog/types")).andExpect(status().isUnauthorized())
  }

  @Test
  fun `list issue types returns types for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          get("/api/work-item-catalog/types")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].code").value("bug"))
  }

  @Test
  fun `create issue type returns created type for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          post("/api/work-item-catalog/types")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "scope": "tenant",
                "code": "task",
                "name": "Task"
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
      .andExpect(jsonPath("$.code").value("task"))
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

    @Bean fun workItemCatalogService(): WorkItemCatalogService = mockk(relaxed = true)

    @Bean fun projectService(): ProjectService = mockk(relaxed = true)

    @Bean
    fun workItemCatalogServiceSetup(catalog: WorkItemCatalogService): Boolean {
      coEvery { catalog.listIssueTypes(TenantWebMvcFixtures.TENANT_ID) } returns listOf(SAMPLE_TYPE)
      coEvery { catalog.createIssueType(any()) } returns
        SAMPLE_TYPE.copy(code = "task", name = "Task")
      return true
    }
  }

  private companion object {
    val SAMPLE_TYPE =
      IssueTypeRecord(
        id = java.util.UUID.randomUUID(),
        apiId = PublicId("typ_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        tenantId = TenantWebMvcFixtures.TENANT_ID,
        scope = WorkItemConfigScope.TENANT,
        projectId = null,
        code = "bug",
        name = "Bug",
        description = null,
        icon = null,
        color = null,
        rank = 100,
        isActive = true,
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )
  }
}
