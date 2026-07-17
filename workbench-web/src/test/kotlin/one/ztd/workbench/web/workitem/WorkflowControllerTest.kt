package one.ztd.workbench.web.workitem

import io.mockk.coEvery
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import java.time.OffsetDateTime
import one.ztd.workbench.agile.workitem.WorkflowConfigurationService
import one.ztd.workbench.agile.workitem.model.WorkflowRecord
import one.ztd.workbench.agile.workitem.model.WorkflowTransitionRecord
import one.ztd.workbench.identity.SessionService
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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(WorkflowController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  AopAutoConfiguration::class,
  InfrastructureAspect::class,
  RequestContextResolver::class,
  TenantRequestContextResolver::class,
  one.ztd.workbench.web.support.ContextWebMvcSupport::class,
  one.ztd.workbench.web.support.ProjectWebMvcSupport::class,
  TenantScopedWebMvcSupport::class,
  GlobalExceptionHandler::class,
  WorkflowControllerTest.TestBeans::class,
)
class WorkflowControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list workflows rejects unauthenticated requests`() {
    mockMvc.perform(get("/api/work-item-catalog/workflows")).andExpect(status().isUnauthorized())
  }

  @Test
  fun `list workflows returns workflows for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          get("/api/work-item-catalog/workflows")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].code").value("default"))
  }

  @Test
  fun `create workflow returns created workflow for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          post("/api/work-item-catalog/workflows")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "code": "support",
                "name": "Support Workflow"
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
      .andExpect(jsonPath("$.code").value("support"))
  }

  @Test
  fun `publish workflow returns published workflow for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          post("/api/work-item-catalog/workflows/${SAMPLE_WORKFLOW.apiId.value}/publish")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.publishedAt").isNotEmpty())
  }

  @Test
  fun `deactivate workflow returns deactivated workflow for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          patch("/api/work-item-catalog/workflows/${SAMPLE_WORKFLOW.apiId.value}/deactivate")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("default"))
  }

  @Test
  fun `list transitions returns transitions for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          get("/api/work-item-catalog/workflows/${SAMPLE_WORKFLOW.apiId.value}/transitions")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].name").value("Start"))
  }

  @Test
  fun `create transition returns created transition for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          post("/api/work-item-catalog/workflows/${SAMPLE_WORKFLOW.apiId.value}/transitions")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "name": "Complete",
                "toStatusId": "sts_01JABCDEFGHJKMNPQRSTVWXYZ1"
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
      .andExpect(jsonPath("$.name").value("Complete"))
  }

  @Test
  fun `deactivate transition returns deactivated transition for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          patch(
              "/api/work-item-catalog/workflows/${SAMPLE_WORKFLOW.apiId.value}/transitions/${SAMPLE_TRANSITION.apiId.value}/deactivate"
            )
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name").value("Start"))
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

    @Bean fun workflowConfigurationService(): WorkflowConfigurationService = mockk(relaxed = true)

    @Bean
    fun workflowConfigurationServiceSetup(service: WorkflowConfigurationService): Boolean {
      coEvery { service.listWorkflows(TenantWebMvcFixtures.TENANT_ID) } returns
        listOf(SAMPLE_WORKFLOW)
      coEvery { service.createWorkflow(any()) } returns
        SAMPLE_WORKFLOW.copy(code = "support", name = "Support Workflow")
      coEvery {
        service.publishWorkflow(TenantWebMvcFixtures.TENANT_ID, SAMPLE_WORKFLOW.apiId.value)
      } returns SAMPLE_WORKFLOW.copy(publishedAt = OffsetDateTime.parse("2026-07-04T12:00:00Z"))
      coEvery {
        service.deactivateWorkflow(
          TenantWebMvcFixtures.TENANT_ID,
          SAMPLE_WORKFLOW.apiId.value,
          TenantWebMvcFixtures.USER_ID,
        )
      } returns SAMPLE_WORKFLOW.copy(isActive = false)
      coEvery {
        service.listTransitions(TenantWebMvcFixtures.TENANT_ID, SAMPLE_WORKFLOW.apiId.value)
      } returns listOf(SAMPLE_TRANSITION)
      coEvery { service.createTransition(any()) } returns SAMPLE_TRANSITION.copy(name = "Complete")
      coEvery {
        service.deactivateTransition(
          TenantWebMvcFixtures.TENANT_ID,
          SAMPLE_WORKFLOW.apiId.value,
          SAMPLE_TRANSITION.apiId.value,
        )
      } returns SAMPLE_TRANSITION.copy(isActive = false)
      return true
    }
  }

  private companion object {
    val SAMPLE_WORKFLOW =
      WorkflowRecord(
        id = java.util.UUID.randomUUID(),
        apiId = PublicId("wfl_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        tenantId = TenantWebMvcFixtures.TENANT_ID,
        code = "default",
        name = "Default Workflow",
        description = "Primary workflow",
        version = 1,
        isActive = true,
        publishedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        createdBy = TenantWebMvcFixtures.USER_ID,
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )
    val SAMPLE_TRANSITION =
      WorkflowTransitionRecord(
        id = java.util.UUID.randomUUID(),
        apiId = PublicId("wft_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        tenantId = TenantWebMvcFixtures.TENANT_ID,
        workflowId = java.util.UUID.randomUUID(),
        name = "Start",
        fromStatusId = null,
        fromStatusApiId = null,
        toStatusId = java.util.UUID.randomUUID(),
        toStatusApiId = PublicId("sts_01JABCDEFGHJKMNPQRSTVWXYZ1"),
        rank = 10,
        preconditionAst = kotlinx.serialization.json.JsonObject(emptyMap()),
        fields = kotlinx.serialization.json.JsonObject(emptyMap()),
        isActive = true,
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )
  }
}
