package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.project.ProjectResolver
import ink.doa.workbench.agile.workitem.WorkItemActivityService
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.activity.WorkItemActivityEntityRef
import ink.doa.workbench.core.workitem.activity.WorkItemActivityListPage
import ink.doa.workbench.core.workitem.activity.WorkItemActivityPageInfo
import ink.doa.workbench.core.workitem.activity.WorkItemActivityPayload
import ink.doa.workbench.core.workitem.activity.WorkItemActivityRecord
import ink.doa.workbench.core.workitem.activity.WorkItemActivitySourceType
import ink.doa.workbench.core.workitem.activity.WorkItemActivityStatusRef
import ink.doa.workbench.core.workitem.activity.WorkItemActivityStatusSnapshot
import ink.doa.workbench.core.workitem.activity.WorkItemActivityType
import ink.doa.workbench.core.workitem.activity.WorkItemCreatedPayload
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
import java.util.UUID
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

@WebMvcTest(ProjectWorkItemActivityController::class)
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
  ProjectWorkItemActivityControllerTest.TestBeans::class,
)
class ProjectWorkItemActivityControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list activities rejects unauthenticated requests`() {
    mockMvc
      .perform(
        get(
          "/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-items/iss_test/activities"
        )
      )
      .andExpect(status().isUnauthorized())
  }

  @Test
  fun `list activities returns typed payload for authenticated user`() {
    val result =
      mockMvc
        .perform(
          get(
              "/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-items/iss_test/activities"
            )
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].id").isNotEmpty())
      .andExpect(jsonPath("$.items[0].type").value("work_item.created"))
      .andExpect(jsonPath("$.items[0].actor.displayName").value("Ada"))
      .andExpect(jsonPath("$.items[0].summary").value("Created with status To Do"))
      .andExpect(jsonPath("$.page.limit").value(50))
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

    @Bean
    fun workItemActivityServiceSetup(service: WorkItemActivityService): Boolean {
      coEvery { service.list(any(), any(), any(), any(), any()) } returns samplePage()
      return true
    }

    @Bean fun workItemActivityService(): WorkItemActivityService = mockk(relaxed = true)
  }

  private companion object {
    fun samplePage(): WorkItemActivityListPage {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val now = OffsetDateTime.parse("2026-07-05T12:00:00Z")
      val actorApiId = PublicId.new("usr")
      return WorkItemActivityListPage(
        items =
          listOf(
            WorkItemActivityRecord(
              id = UUID.randomUUID(),
              apiId = PublicId.new("act"),
              tenantId = tenantId,
              projectId = projectId,
              workItemId = UUID.randomUUID(),
              workItemApiId = PublicId.new("iss"),
              actorUserId = UUID.randomUUID(),
              actorApiId = actorApiId,
              actorDisplayName = "Ada",
              activityType = WorkItemActivityType.CREATED,
              occurredAt = now,
              summary = "Created with status To Do",
              payload =
                WorkItemActivityPayload.Created(
                  WorkItemCreatedPayload(
                    status =
                      WorkItemActivityStatusSnapshot(
                        to = WorkItemActivityStatusRef("sts_test", "To Do", "todo")
                      ),
                    issueType = WorkItemActivityEntityRef("ity_test", "Task"),
                  )
                ),
              sourceType = WorkItemActivitySourceType.USER,
              createdAt = now,
            )
          ),
        page = WorkItemActivityPageInfo(limit = 50, nextBefore = null),
      )
    }
  }
}
