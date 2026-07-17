package one.ztd.workbench.web.workitem

import io.mockk.coEvery
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import java.time.OffsetDateTime
import one.ztd.workbench.agile.project.ProjectResolver
import one.ztd.workbench.agile.workitem.WorkItemCommentService
import one.ztd.workbench.agile.workitem.model.WorkItemCommentRecord
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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ProjectWorkItemCommentController::class)
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
  ProjectWorkItemCommentControllerTest.TestBeans::class,
)
class ProjectWorkItemCommentControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `create comment returns created comment for authenticated user`() {
    val result =
      mockMvc
        .perform(
          post(
              "/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-items/iss_test/comments"
            )
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(commentRequest("New comment"))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isCreated())
      .andExpect(
        header()
          .string(
            "Location",
            "/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-items/iss_test/comments/${SAMPLE_COMMENT.apiId.value}",
          )
      )
      .andExpect(jsonPath("$.body.content.content[0].content[0].text").value("New comment"))
  }

  @Test
  fun `update comment returns updated comment for authenticated user`() {
    val result =
      mockMvc
        .perform(
          patch(
              "/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-items/iss_test/comments/${SAMPLE_COMMENT.apiId.value}"
            )
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(commentRequest("Updated comment"))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.body.content.content[0].content[0].text").value("Updated comment"))
  }

  @Test
  fun `delete comment returns no content for authenticated user`() {
    val result =
      mockMvc
        .perform(
          delete(
              "/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-items/iss_test/comments/${SAMPLE_COMMENT.apiId.value}"
            )
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent())
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
    fun projectResolverSetup(resolver: ProjectResolver): Boolean {
      coEvery {
        resolver.resolveProject(
          TenantWebMvcFixtures.TENANT_ID,
          TenantWebMvcFixtures.PROJECT_PUBLIC_ID,
        )
      } returns TenantWebMvcFixtures.PROJECT_RECORD
      return true
    }

    @Bean fun workItemCommentService(): WorkItemCommentService = mockk(relaxed = true)

    @Bean
    fun workItemCommentServiceSetup(service: WorkItemCommentService): Boolean {
      coEvery {
        service.create(
          one.ztd.workbench.agile.workitem.model.CreateWorkItemCommentCommand(
            tenantId = TenantWebMvcFixtures.TENANT_ID,
            projectId = TenantWebMvcFixtures.PROJECT_ID,
            workItemApiId = "iss_test",
            authorId = TenantWebMvcFixtures.USER_ID,
            body = richText("New comment"),
          )
        )
      } returns SAMPLE_COMMENT.copy(body = richText("New comment"), bodyPlainText = "New comment")
      coEvery {
        service.update(
          one.ztd.workbench.agile.workitem.model.UpdateWorkItemCommentCommand(
            tenantId = TenantWebMvcFixtures.TENANT_ID,
            projectId = TenantWebMvcFixtures.PROJECT_ID,
            workItemApiId = "iss_test",
            commentApiId = SAMPLE_COMMENT.apiId.value,
            actorUserId = TenantWebMvcFixtures.USER_ID,
            body = richText("Updated comment"),
          )
        )
      } returns
        SAMPLE_COMMENT.copy(body = richText("Updated comment"), bodyPlainText = "Updated comment")
      coEvery {
        service.delete(
          one.ztd.workbench.agile.workitem.model.DeleteWorkItemCommentCommand(
            tenantId = TenantWebMvcFixtures.TENANT_ID,
            projectId = TenantWebMvcFixtures.PROJECT_ID,
            workItemApiId = "iss_test",
            commentApiId = SAMPLE_COMMENT.apiId.value,
            actorUserId = TenantWebMvcFixtures.USER_ID,
          )
        )
      } returns SAMPLE_COMMENT
      return true
    }
  }

  private companion object {
    val SAMPLE_COMMENT =
      WorkItemCommentRecord(
        id = java.util.UUID.randomUUID(),
        apiId = PublicId("cmt_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        tenantId = TenantWebMvcFixtures.TENANT_ID,
        issueId = java.util.UUID.randomUUID(),
        authorId = TenantWebMvcFixtures.USER_ID,
        authorApiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
        body = richText("Looks good"),
        bodyPlainText = "Looks good",
        transitionId = null,
        statusHistoryId = null,
        editedAt = null,
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )
  }
}

private fun richText(value: String) =
  one.ztd.workbench.agile.workitem.richtext.RichTextProcessor.fromPlainText(value)!!

private fun commentRequest(value: String) =
  """{"body":{"format":"tiptap","schemaVersion":1,"content":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"$value"}]}]}}}"""
