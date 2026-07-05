package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.project.ProjectResolver
import ink.doa.workbench.agile.workitem.WorkItemTimelineService
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.common.pagination.WorkbenchCursor
import ink.doa.workbench.core.common.pagination.WorkbenchTimelineEntryKind
import ink.doa.workbench.core.workitem.activity.WorkItemActivityEntityRef
import ink.doa.workbench.core.workitem.activity.WorkItemActivityPayload
import ink.doa.workbench.core.workitem.activity.WorkItemActivityRecord
import ink.doa.workbench.core.workitem.activity.WorkItemActivitySourceType
import ink.doa.workbench.core.workitem.activity.WorkItemActivityStatusRef
import ink.doa.workbench.core.workitem.activity.WorkItemActivityStatusSnapshot
import ink.doa.workbench.core.workitem.activity.WorkItemActivityType
import ink.doa.workbench.core.workitem.activity.WorkItemCreatedPayload
import ink.doa.workbench.core.workitem.model.WorkItemCommentRecord
import ink.doa.workbench.core.workitem.timeline.WorkItemTimelineEntry
import ink.doa.workbench.core.workitem.timeline.WorkItemTimelinePage
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
import ink.doa.workbench.security.identity.SessionService
import ink.doa.workbench.web.api.GlobalExceptionHandler
import ink.doa.workbench.web.api.InfrastructureAspect
import ink.doa.workbench.web.api.ProjectRequestContextResolver
import ink.doa.workbench.web.api.RequestContextResolver
import ink.doa.workbench.web.api.TenantRequestContextResolver
import ink.doa.workbench.web.api.http.WORKBENCH_NEXT_CURSOR_HEADER
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ProjectWorkItemTimelineController::class)
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
  ProjectWorkItemTimelineControllerTest.TestBeans::class,
)
class ProjectWorkItemTimelineControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list timeline rejects unauthenticated requests`() {
    mockMvc
      .perform(
        get("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-items/iss_test/timeline")
      )
      .andExpect(status().isUnauthorized())
  }

  @Test
  fun `list timeline returns entries and next cursor header for authenticated user`() {
    val result =
      mockMvc
        .perform(
          get(
              "/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-items/iss_test/timeline"
            )
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].kind").value("activity"))
      .andExpect(jsonPath("$[0].id").isNotEmpty())
      .andExpect(jsonPath("$[0].type").value("work_item.created"))
      .andExpect(jsonPath("$[0].actor.displayName").value("Ada"))
      .andExpect(jsonPath("$[0].summary").value("Created with status To Do"))
      .andExpect(jsonPath("$[1].kind").value("comment"))
      .andExpect(jsonPath("$[1].body").value("<p>Ship it</p>"))
      .andExpect(header().exists(WORKBENCH_NEXT_CURSOR_HEADER))
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

    @Bean fun workItemTimelineService(): WorkItemTimelineService = mockk(relaxed = true)

    @Bean
    fun workItemTimelineServiceSetup(service: WorkItemTimelineService): Boolean {
      coEvery { service.list(any(), any(), any(), any(), any()) } returns samplePage()
      return true
    }

    private fun samplePage(): WorkItemTimelinePage {
      val now = OffsetDateTime.parse("2026-07-03T12:00:00Z")
      val tenantId = TenantWebMvcFixtures.TENANT_ID
      val projectId = TenantWebMvcFixtures.PROJECT_ID
      val workItemId = UUID.randomUUID()
      return WorkItemTimelinePage(
        items =
          listOf(
            WorkItemTimelineEntry.Activity(
              WorkItemActivityRecord(
                id = UUID.randomUUID(),
                apiId = PublicId.new("act"),
                tenantId = tenantId,
                projectId = projectId,
                workItemId = workItemId,
                workItemApiId = PublicId.new("iss"),
                actorUserId = TenantWebMvcFixtures.USER_ID,
                actorApiId = PublicId.new("usr"),
                actorDisplayName = "Ada",
                activityType = WorkItemActivityType.CREATED,
                occurredAt = now,
                summary = "Created with status To Do",
                payload =
                  WorkItemActivityPayload.Created(
                    WorkItemCreatedPayload(
                      status =
                        WorkItemActivityStatusSnapshot(
                          to = WorkItemActivityStatusRef("sts_open", "To Do", "todo")
                        ),
                      issueType = WorkItemActivityEntityRef("ity_task", "Task"),
                    )
                  ),
                sourceType = WorkItemActivitySourceType.USER,
                createdAt = now,
              )
            ),
            WorkItemTimelineEntry.Comment(
              WorkItemCommentRecord(
                id = UUID.randomUUID(),
                apiId = PublicId.new("icm"),
                tenantId = tenantId,
                issueId = workItemId,
                authorId = TenantWebMvcFixtures.USER_ID,
                authorApiId = PublicId.new("usr"),
                body = "<p>Ship it</p>",
                bodyPlainText = "Ship it",
                bodyFormat = "html",
                transitionId = null,
                statusHistoryId = null,
                activityId = null,
                editedAt = null,
                createdAt = now.minusMinutes(1),
                updatedAt = now.minusMinutes(1),
              )
            ),
          ),
        nextCursor =
          WorkbenchCursor(
            occurredAt = now.minusMinutes(1),
            entryKind = WorkbenchTimelineEntryKind.COMMENT,
            entryId = UUID.randomUUID(),
          ),
      )
    }
  }
}
