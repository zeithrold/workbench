package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.workitem.WorkflowConfigurationService
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.WorkflowRecord
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
import ink.doa.workbench.security.identity.SessionService
import ink.doa.workbench.web.api.GlobalExceptionHandler
import ink.doa.workbench.web.api.InfrastructureAspect
import ink.doa.workbench.web.api.RequestContextResolver
import ink.doa.workbench.web.api.TenantRequestContextResolver
import ink.doa.workbench.web.support.TenantScopedWebMvcSupport
import ink.doa.workbench.web.support.TenantWebMvcFixtures
import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(WorkflowController::class)
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
  WorkflowControllerTest.TestBeans::class,
)
class WorkflowControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list workflows rejects unauthenticated requests`() {
    mockMvc.perform(get("/api/workflows")).andExpect(status().isUnauthorized())
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

    @Bean fun workflowConfigurationService(): WorkflowConfigurationService = mockk(relaxed = true)

    @Bean
    fun workflowConfigurationServiceSetup(service: WorkflowConfigurationService): Boolean {
      coEvery { service.listWorkflows(TenantWebMvcFixtures.TENANT_ID) } returns
        listOf(SAMPLE_WORKFLOW)
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
  }
}
