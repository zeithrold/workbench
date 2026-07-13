package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.project.ProjectService
import ink.doa.workbench.agile.workitem.IssueTypeConfigAccessRuleService
import ink.doa.workbench.agile.workitem.IssueTypeConfigService
import ink.doa.workbench.agile.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.agile.workitem.model.IssueTypeConfigRecord
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
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(WorkItemTypeConfigController::class)
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
  WorkItemTypeConfigControllerTest.TestBeans::class,
)
class WorkItemTypeConfigControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list type configs rejects unauthenticated requests`() {
    mockMvc.perform(get("/api/work-item-catalog/type-configs")).andExpect(status().isUnauthorized())
  }

  @Test
  fun `list type configs returns configs for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          get("/api/work-item-catalog/type-configs")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].issueTypeId").value("typ_01JABCDEFGHJKMNPQRSTVWXYZ0"))
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

    @Bean fun issueTypeConfigService(): IssueTypeConfigService = mockk(relaxed = true)

    @Bean
    fun issueTypeConfigAccessRuleService(): IssueTypeConfigAccessRuleService = mockk(relaxed = true)

    @Bean fun projectService(): ProjectService = mockk(relaxed = true)

    @Bean
    fun issueTypeConfigServiceSetup(configs: IssueTypeConfigService): Boolean {
      coEvery { configs.list(TenantWebMvcFixtures.TENANT_ID) } returns
        listOf(
          IssueTypeConfigDetails(
            config = SAMPLE_CONFIG,
            statuses = emptyList(),
            properties = emptyList(),
          )
        )
      return true
    }
  }

  private companion object {
    val SAMPLE_CONFIG =
      IssueTypeConfigRecord(
        id = java.util.UUID.randomUUID(),
        apiId = PublicId("itc_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        tenantId = TenantWebMvcFixtures.TENANT_ID,
        scope = WorkItemConfigScope.TENANT,
        projectId = null,
        issueTypeId = java.util.UUID.randomUUID(),
        issueTypeApiId = PublicId("typ_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        workflowId = java.util.UUID.randomUUID(),
        workflowApiId = PublicId("wfl_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        version = 1,
        nameOverride = null,
        iconOverride = null,
        colorOverride = null,
        rank = 100,
        isActive = true,
        validFrom = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        validTo = null,
        createdBy = TenantWebMvcFixtures.USER_ID,
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        createFields = JsonObject(emptyMap()),
      )
  }
}
