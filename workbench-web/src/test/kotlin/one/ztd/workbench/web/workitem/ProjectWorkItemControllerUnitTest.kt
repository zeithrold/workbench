package one.ztd.workbench.web.workitem

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.agile.workitem.WorkItemQueryService
import one.ztd.workbench.agile.workitem.WorkItemSearchPageRequest
import one.ztd.workbench.agile.workitem.WorkItemSearchScope
import one.ztd.workbench.agile.workitem.WorkItemService
import one.ztd.workbench.agile.workitem.WorkItemTransitionService
import one.ztd.workbench.agile.workitem.model.WorkItemIssueTypeSummary
import one.ztd.workbench.agile.workitem.model.WorkItemSearchHit
import one.ztd.workbench.agile.workitem.model.WorkItemSearchResult
import one.ztd.workbench.agile.workitem.model.WorkItemStatusSummary
import one.ztd.workbench.agile.workitem.model.WorkItemUserSummary
import one.ztd.workbench.agile.workitem.query.WorkItemSearchGroupScope
import one.ztd.workbench.kernel.common.context.InstanceContextSummary
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.kernel.common.pagination.WorkItemSearchCursor
import one.ztd.workbench.web.api.context.ApiVersion
import one.ztd.workbench.web.api.context.ProjectContextSummary
import one.ztd.workbench.web.api.context.ProjectRequestContext
import one.ztd.workbench.web.api.context.TenantContextSummary
import one.ztd.workbench.web.api.context.UserContextSummary
import one.ztd.workbench.web.api.http.WORKBENCH_NEXT_CURSOR_HEADER
import one.ztd.workbench.web.support.TenantWebMvcFixtures

class ProjectWorkItemControllerUnitTest :
  StringSpec({
    val mapper = ObjectMapper()
    val service = mockk<WorkItemService>(relaxed = true)
    val transitionService = mockk<WorkItemTransitionService>(relaxed = true)
    val queryService = mockk<WorkItemQueryService>()
    val controller = ProjectWorkItemController(service, transitionService, queryService)
    val projectContext =
      ProjectRequestContext(
        requestId = "req",
        apiVersion = ApiVersion.Default,
        actor =
          UserContextSummary(
            id = TenantWebMvcFixtures.USER_ID,
            publicId = TenantWebMvcFixtures.PRINCIPAL.user.apiId,
            displayName = TenantWebMvcFixtures.PRINCIPAL.user.displayName,
            primaryEmail = TenantWebMvcFixtures.PRINCIPAL.user.primaryEmail,
          ),
        receivedAt = Instant.parse("2026-07-04T00:00:00Z"),
        instance = InstanceContextSummary(id = "default", name = "Default"),
        tenant =
          TenantContextSummary(
            id = TenantWebMvcFixtures.TENANT_ID,
            publicId = TenantWebMvcFixtures.TENANT_RECORD.apiId,
            slug = TenantWebMvcFixtures.TENANT_RECORD.slug,
            name = TenantWebMvcFixtures.TENANT_RECORD.name,
          ),
        project =
          ProjectContextSummary(
            id = TenantWebMvcFixtures.PROJECT_ID,
            publicId = PublicId(TenantWebMvcFixtures.PROJECT_PUBLIC_ID),
            identifier = TenantWebMvcFixtures.PROJECT_RECORD.identifier,
            name = TenantWebMvcFixtures.PROJECT_RECORD.name,
          ),
      )
    val nextCursor =
      WorkItemSearchCursor(sortValues = listOf(JsonPrimitive("todo")), apiId = "iss_next")
    val searchResult =
      WorkItemSearchResult(
        hits =
          listOf(
            WorkItemSearchHit(
              databaseId = UUID.randomUUID(),
              apiId = "iss_01JABCDEFGHJKMNPQRSTVWXYZ0",
              key = "CORE-1",
              title = "Fix login",
              description = null,
              projectApiId = TenantWebMvcFixtures.PROJECT_PUBLIC_ID,
              issueType =
                WorkItemIssueTypeSummary(
                  id = "typ_01JABCDEFGHJKMNPQRSTVWXYZ0",
                  code = "bug",
                  name = "Bug",
                  icon = "bug",
                  color = "#ef4444",
                ),
              issueTypeConfigApiId = "itc_01JABCDEFGHJKMNPQRSTVWXYZ0",
              status =
                WorkItemStatusSummary(
                  id = "sts_01JABCDEFGHJKMNPQRSTVWXYZ0",
                  code = "todo",
                  name = "Todo",
                  group = "todo",
                  color = null,
                  terminal = false,
                ),
              priority = null,
              reporter = WorkItemUserSummary("usr_01JABCDEFGHJKMNPQRSTVWXYZ1", "Alice"),
              assignee = null,
              sprint = null,
              createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
              updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
              properties = emptyMap(),
            )
          ),
        nextCursor = nextCursor,
      )

    "search delegates to query service with parsed request and cursor header" {
      coEvery { queryService.search(any(), any(), any(), any()) } returns searchResult

      val response = runBlocking {
        controller.search(
          WorkItemSearchRequest(
            query =
              mapper.readTree(
                """
                {
                  "version": 1,
                  "resource": "work_item",
                  "where": { "field": "title", "op": "contains", "value": "login" }
                }
                """
                  .trimIndent()
              ),
            limit = 25,
            cursor = nextCursor.encode(),
          ),
          projectContext,
        )
      }

      response.body?.single()?.key shouldBe "CORE-1"
      response.headers.getFirst(WORKBENCH_NEXT_CURSOR_HEADER) shouldBe nextCursor.encode()
      coVerify {
        queryService.search(
          scope =
            WorkItemSearchScope(TenantWebMvcFixtures.TENANT_ID, TenantWebMvcFixtures.PROJECT_ID),
          query = any(),
          groupScope = WorkItemSearchGroupScope(),
          page =
            WorkItemSearchPageRequest(
              limit = 25,
              cursor = nextCursor,
            ),
        )
      }
    }
  })
