package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.workitem.WorkItemCatalogService
import ink.doa.workbench.agile.workitem.model.IssueStatusRecord
import ink.doa.workbench.agile.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.identity.SessionService
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
import ink.doa.workbench.web.api.GlobalExceptionHandler
import ink.doa.workbench.web.api.InfrastructureAspect
import ink.doa.workbench.web.api.RequestContextResolver
import ink.doa.workbench.web.api.TenantRequestContextResolver
import ink.doa.workbench.web.support.ContextWebMvcSupport
import ink.doa.workbench.web.support.ProjectWebMvcSupport
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

@WebMvcTest(WorkItemStatusController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  AopAutoConfiguration::class,
  InfrastructureAspect::class,
  RequestContextResolver::class,
  TenantRequestContextResolver::class,
  ContextWebMvcSupport::class,
  ProjectWebMvcSupport::class,
  TenantScopedWebMvcSupport::class,
  GlobalExceptionHandler::class,
  WorkItemStatusControllerTest.TestBeans::class,
)
class WorkItemStatusControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list statuses rejects unauthenticated requests`() {
    mockMvc.perform(get("/api/work-item-catalog/statuses")).andExpect(status().isUnauthorized())
  }

  @Test
  fun `list statuses returns statuses for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          get("/api/work-item-catalog/statuses")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].code").value("todo"))
  }

  @Test
  fun `create status returns created status for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          post("/api/work-item-catalog/statuses")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "code": "review",
                "name": "In Review",
                "statusGroup": "in_progress"
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
      .andExpect(jsonPath("$.code").value("review"))
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

    @Bean
    fun workItemCatalogServiceSetup(service: WorkItemCatalogService): Boolean {
      coEvery { service.listStatuses(TenantWebMvcFixtures.TENANT_ID) } returns listOf(SAMPLE_STATUS)
      coEvery { service.createStatus(any()) } returns
        SAMPLE_STATUS.copy(code = "review", name = "In Review")
      return true
    }
  }

  private companion object {
    val SAMPLE_STATUS =
      IssueStatusRecord(
        id = java.util.UUID.randomUUID(),
        apiId = PublicId("sts_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        tenantId = TenantWebMvcFixtures.TENANT_ID,
        code = "todo",
        name = "To Do",
        statusGroup = WorkItemStatusGroup.TODO,
        rank = 10,
        color = null,
        isTerminal = false,
        isActive = true,
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )
  }
}
