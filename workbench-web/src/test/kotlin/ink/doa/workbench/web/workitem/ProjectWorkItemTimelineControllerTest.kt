package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.project.ProjectResolver
import ink.doa.workbench.agile.workitem.WorkItemTimelineService
import ink.doa.workbench.agile.workitem.activity.WorkItemActivityCommentRef
import ink.doa.workbench.agile.workitem.activity.WorkItemActivityEntityRef
import ink.doa.workbench.agile.workitem.activity.WorkItemActivityPayload
import ink.doa.workbench.agile.workitem.activity.WorkItemActivityStatusRef
import ink.doa.workbench.agile.workitem.activity.WorkItemActivityStatusSnapshot
import ink.doa.workbench.agile.workitem.activity.WorkItemCommentCreatedPayload
import ink.doa.workbench.agile.workitem.activity.WorkItemCreatedPayload
import ink.doa.workbench.agile.workitem.model.WorkItemCommentRecord
import ink.doa.workbench.agile.workitem.stream.WorkItemEventRecord
import ink.doa.workbench.agile.workitem.stream.WorkItemEventSourceType
import ink.doa.workbench.agile.workitem.stream.WorkItemEventType
import ink.doa.workbench.agile.workitem.timeline.WorkItemTimelineEntry
import ink.doa.workbench.agile.workitem.timeline.WorkItemTimelinePage
import ink.doa.workbench.identity.SessionService
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.kernel.common.pagination.WorkItemStreamCursor
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
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
      .andExpect(jsonPath("$[0].type").value("work_item.created"))
      .andExpect(jsonPath("$[0].id").isNotEmpty())
      .andExpect(jsonPath("$[0].sequence").value(2))
      .andExpect(jsonPath("$[0].actor.displayName").value("Ada"))
      .andExpect(jsonPath("$[0].summary").value("Created with status To Do"))
      .andExpect(jsonPath("$[1].type").value("comment.added"))
      .andExpect(jsonPath("$[1].body.content.content[0].content[0].text").value("Ship it"))
      .andExpect(header().exists(WORKBENCH_NEXT_CURSOR_HEADER))
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
      val commentId = UUID.randomUUID()
      val commentApiId = PublicId.new("icm")
      val commentEvent =
        WorkItemEventRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("evt"),
          tenantId = tenantId,
          projectId = projectId,
          workItemId = workItemId,
          workItemApiId = PublicId.new("iss"),
          sequence = 1,
          eventType = WorkItemEventType.COMMENT_ADDED,
          actorUserId = TenantWebMvcFixtures.USER_ID,
          actorApiId = PublicId.new("usr"),
          actorDisplayName = "Ada",
          occurredAt = now.minusMinutes(1),
          summary = null,
          payload =
            WorkItemActivityPayload.CommentAdded(
              WorkItemCommentCreatedPayload(
                comment = WorkItemActivityCommentRef(commentApiId.value, "Ship it")
              )
            ),
          sourceType = WorkItemEventSourceType.USER,
          createdAt = now.minusMinutes(1),
        )
      return WorkItemTimelinePage(
        items =
          listOf(
            WorkItemTimelineEntry.Event(
              WorkItemEventRecord(
                id = UUID.randomUUID(),
                apiId = PublicId.new("evt"),
                tenantId = tenantId,
                projectId = projectId,
                workItemId = workItemId,
                workItemApiId = PublicId.new("iss"),
                sequence = 2,
                eventType = WorkItemEventType.CREATED,
                actorUserId = TenantWebMvcFixtures.USER_ID,
                actorApiId = PublicId.new("usr"),
                actorDisplayName = "Ada",
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
                sourceType = WorkItemEventSourceType.USER,
                createdAt = now,
              )
            ),
            WorkItemTimelineEntry.Comment(
              event = commentEvent,
              comment =
                WorkItemCommentRecord(
                  id = commentId,
                  apiId = commentApiId,
                  tenantId = tenantId,
                  issueId = workItemId,
                  authorId = TenantWebMvcFixtures.USER_ID,
                  authorApiId = PublicId.new("usr"),
                  body =
                    ink.doa.workbench.agile.workitem.richtext.RichTextProcessor.fromPlainText(
                      "Ship it"
                    )!!,
                  bodyPlainText = "Ship it",
                  transitionId = null,
                  statusHistoryId = null,
                  editedAt = null,
                  createdAt = now.minusMinutes(1),
                  updatedAt = now.minusMinutes(1),
                ),
            ),
          ),
        nextCursor = WorkItemStreamCursor(sequence = 1),
      )
    }
  }
}
