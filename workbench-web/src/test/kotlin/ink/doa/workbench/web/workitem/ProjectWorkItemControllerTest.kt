package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.project.ProjectResolver
import ink.doa.workbench.agile.workitem.WorkItemQueryService
import ink.doa.workbench.agile.workitem.WorkItemService
import ink.doa.workbench.agile.workitem.WorkItemTransitionService
import ink.doa.workbench.agile.workitem.model.WorkItemCreateFormOption
import ink.doa.workbench.agile.workitem.model.WorkItemFormFieldMeta
import ink.doa.workbench.agile.workitem.model.WorkItemMutationResult
import ink.doa.workbench.agile.workitem.model.WorkItemRecord
import ink.doa.workbench.agile.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.agile.workitem.model.WorkItemTransitionOption
import ink.doa.workbench.identity.SessionService
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
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
import kotlinx.serialization.json.JsonObject
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ProjectWorkItemController::class)
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
  ProjectWorkItemControllerTest.TestBeans::class,
)
class ProjectWorkItemControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list work items rejects unauthenticated requests`() {
    mockMvc
      .perform(get("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-items"))
      .andExpect(status().isUnauthorized())
  }

  @Test
  fun `list work items returns project issues for authenticated user`() {
    val result =
      mockMvc
        .perform(
          get("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-items")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].title").value("Fix login"))
      .andExpect(jsonPath("$[0].key").value("CORE-1"))
  }

  @Test
  fun `get work item returns issue for authenticated user`() {
    val result =
      mockMvc
        .perform(
          get(
              "/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-items/${SAMPLE_WORK_ITEM.apiId.value}"
            )
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.title").value("Fix login"))
      .andExpect(jsonPath("$.key").value("CORE-1"))
  }

  @Test
  fun `create work item returns created issue for authenticated user`() {
    val result =
      mockMvc
        .perform(
          post("/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-items")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "issueTypeId": "typ_01JABCDEFGHJKMNPQRSTVWXYZ0",
                "title": "New issue"
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
      .andExpect(jsonPath("$.title").value("New issue"))
  }

  @Test
  fun `update work item returns updated issue for authenticated user`() {
    val result =
      mockMvc
        .perform(
          patch(
              "/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-items/${SAMPLE_WORK_ITEM.apiId.value}"
            )
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"title":"Updated title"}""")
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.title").value("Updated title"))
  }

  @Test
  fun `delete work item returns no content for authenticated user`() {
    val result =
      mockMvc
        .perform(
          delete(
              "/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-items/${SAMPLE_WORK_ITEM.apiId.value}"
            )
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"deleteReason":"duplicate"}""")
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent())
  }

  @Test
  fun `list transitions returns options for authenticated user`() {
    val result =
      mockMvc
        .perform(
          get(
              "/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-items/${SAMPLE_WORK_ITEM.apiId.value}/transitions"
            )
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].name").value("Start progress"))
  }

  @Test
  fun `transition work item returns updated issue for authenticated user`() {
    val result =
      mockMvc
        .perform(
          post(
              "/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-items/${SAMPLE_WORK_ITEM.apiId.value}/transitions"
            )
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"transitionId":"trn_01JABCDEFGHJKMNPQRSTVWXYZ0"}""")
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.statusGroup").value("in_progress"))
  }

  @Test
  fun `create form returns editable fields for authenticated user`() {
    val result =
      mockMvc
        .perform(
          get(
              "/api/projects/${TenantWebMvcFixtures.PROJECT_PUBLIC_ID}/work-items/create-form?issueTypeId=typ_01JABCDEFGHJKMNPQRSTVWXYZ0"
            )
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.editableFields[0]").value("title"))
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

    @Bean fun workItemService(): WorkItemService = mockk(relaxed = true)

    @Bean fun workItemQueryService(): WorkItemQueryService = mockk(relaxed = true)

    @Bean fun workItemTransitionService(): WorkItemTransitionService = mockk(relaxed = true)

    @Bean
    fun workItemServiceSetup(service: WorkItemService): Boolean {
      val createdWorkItem = SAMPLE_WORK_ITEM.copy(title = "New issue", key = "CORE-2")
      val updatedWorkItem = SAMPLE_WORK_ITEM.copy(title = "Updated title")
      coEvery {
        service.list(TenantWebMvcFixtures.TENANT_ID, TenantWebMvcFixtures.PROJECT_ID, 50, 0)
      } returns listOf(SAMPLE_WORK_ITEM)
      coEvery {
        service.get(
          TenantWebMvcFixtures.TENANT_ID,
          TenantWebMvcFixtures.PROJECT_ID,
          SAMPLE_WORK_ITEM.apiId.value,
        )
      } returns SAMPLE_WORK_ITEM
      coEvery { service.create(any()) } returns
        WorkItemMutationResult(workItem = createdWorkItem, eventType = "work_item.created")
      coEvery { service.update(any()) } returns
        WorkItemMutationResult(workItem = updatedWorkItem, eventType = "work_item.updated")
      coEvery { service.delete(any()) } returns
        WorkItemMutationResult(workItem = SAMPLE_WORK_ITEM, eventType = "work_item.deleted")
      coEvery {
        service.availableCreateForm(
          TenantWebMvcFixtures.TENANT_ID,
          TenantWebMvcFixtures.PROJECT_ID,
          "typ_01JABCDEFGHJKMNPQRSTVWXYZ0",
          TenantWebMvcFixtures.USER_ID,
        )
      } returns
        WorkItemCreateFormOption(
          issueTypeId = PublicId("typ_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          initialStatusId = PublicId("sts_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          fields = JsonObject(emptyMap()),
          editableFields = listOf("title"),
          fieldMeta =
            listOf(
              WorkItemFormFieldMeta(path = "title", editable = true, participation = "required")
            ),
        )
      return true
    }

    @Bean
    fun workItemTransitionServiceSetup(transitionService: WorkItemTransitionService): Boolean {
      coEvery {
        transitionService.availableTransitions(
          TenantWebMvcFixtures.TENANT_ID,
          TenantWebMvcFixtures.PROJECT_ID,
          SAMPLE_WORK_ITEM.apiId.value,
          TenantWebMvcFixtures.USER_ID,
          TenantWebMvcFixtures.PRINCIPAL.user.apiId.value,
        )
      } returns
        listOf(
          WorkItemTransitionOption(
            id = PublicId("trn_01JABCDEFGHJKMNPQRSTVWXYZ0"),
            name = "Start progress",
            fromStatusId = PublicId("sts_01JABCDEFGHJKMNPQRSTVWXYZ0"),
            toStatusId = PublicId("sts_01JABCDEFGHJKMNPQRSTVWXYZ1"),
            enabled = true,
            fields = JsonObject(emptyMap()),
          )
        )
      coEvery { transitionService.transition(any()) } returns
        WorkItemMutationResult(
          workItem =
            SAMPLE_WORK_ITEM.copy(
              title = "Updated title",
              statusGroup = WorkItemStatusGroup.IN_PROGRESS,
            ),
          eventType = "work_item.transitioned",
        )
      return true
    }
  }

  private companion object {
    val SAMPLE_WORK_ITEM =
      WorkItemRecord(
        id = java.util.UUID.randomUUID(),
        apiId = PublicId("iss_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        tenantId = TenantWebMvcFixtures.TENANT_ID,
        projectId = TenantWebMvcFixtures.PROJECT_ID,
        issueTypeApiId = PublicId("typ_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        issueTypeConfigApiId = PublicId("itc_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        key = "CORE-1",
        title = "Fix login",
        description = null,
        statusId = java.util.UUID.randomUUID(),
        statusApiId = PublicId("sts_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        statusGroup = WorkItemStatusGroup.TODO,
        reporterId = TenantWebMvcFixtures.USER_ID,
        assigneeId = null,
        priorityApiId = null,
        reporterApiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
        assigneeApiId = null,
        sprintApiId = null,
        properties = JsonObject(emptyMap()),
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )
  }
}
