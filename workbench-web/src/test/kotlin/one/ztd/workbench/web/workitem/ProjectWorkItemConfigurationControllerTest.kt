package one.ztd.workbench.web.workitem

import io.mockk.coEvery
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonObject
import one.ztd.workbench.agile.workitem.IssueTypeConfigService
import one.ztd.workbench.agile.workitem.model.EffectiveIssueTypeConfig
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigDetails
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigRecord
import one.ztd.workbench.agile.workitem.model.WorkItemConfigScope
import one.ztd.workbench.identity.SessionService
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ProjectWorkItemConfigurationController::class)
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
  ProjectWorkItemConfigurationControllerTest.TestBeans::class,
)
class ProjectWorkItemConfigurationControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `effective config rejects unauthenticated requests`() {
    mockMvc
      .perform(
        get(
          "/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-item-types/typ_01JABCDEFGHJKMNPQRSTVWXYZ0/effective-config"
        )
      )
      .andExpect(status().isUnauthorized())
  }

  @Test
  fun `effective config returns resolved config for authenticated user`() {
    val result =
      mockMvc
        .perform(
          get(
              "/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-item-types/typ_01JABCDEFGHJKMNPQRSTVWXYZ0/effective-config"
            )
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.resolvedFrom").value("project"))
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
    fun projectResolverSetup(resolver: one.ztd.workbench.agile.project.ProjectResolver): Boolean {
      coEvery {
        resolver.resolveProject(
          TenantWebMvcFixtures.TENANT_ID,
          TenantWebMvcFixtures.PROJECT_PUBLIC_ID,
        )
      } returns TenantWebMvcFixtures.PROJECT_RECORD
      return true
    }

    @Bean fun issueTypeConfigService(): IssueTypeConfigService = mockk(relaxed = true)

    @Bean
    fun issueTypeConfigServiceSetup(configs: IssueTypeConfigService): Boolean {
      coEvery {
        configs.resolveEffective(
          tenantId = TenantWebMvcFixtures.TENANT_ID,
          projectId = TenantWebMvcFixtures.PROJECT_ID,
          issueTypeApiIdOrCode = "typ_01JABCDEFGHJKMNPQRSTVWXYZ0",
        )
      } returns
        EffectiveIssueTypeConfig(
          config =
            IssueTypeConfigDetails(
              config = SAMPLE_CONFIG,
              statuses = emptyList(),
              properties = emptyList(),
            ),
          resolvedFrom = WorkItemConfigScope.PROJECT,
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
        scope = WorkItemConfigScope.PROJECT,
        projectId = TenantWebMvcFixtures.PROJECT_ID,
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
