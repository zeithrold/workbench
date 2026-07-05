package ink.doa.workbench.web.workitem

import com.fasterxml.jackson.databind.ObjectMapper
import ink.doa.workbench.agile.workitem.WorkItemQueryService
import ink.doa.workbench.agile.workitem.WorkItemService
import ink.doa.workbench.agile.workitem.WorkItemTransitionService
import ink.doa.workbench.core.common.context.ApiVersion
import ink.doa.workbench.core.common.context.InstanceContextSummary
import ink.doa.workbench.core.common.context.ProjectContextSummary
import ink.doa.workbench.core.common.context.ProjectRequestContext
import ink.doa.workbench.core.common.context.TenantContextSummary
import ink.doa.workbench.core.common.context.UserContextSummary
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.WorkItemSearchPageRequest
import ink.doa.workbench.core.workitem.WorkItemSearchScope
import ink.doa.workbench.core.workitem.model.WorkItemSearchHit
import ink.doa.workbench.core.workitem.model.WorkItemSearchPage
import ink.doa.workbench.core.workitem.model.WorkItemSearchPageInfo
import ink.doa.workbench.core.workitem.model.WorkItemSearchResult
import ink.doa.workbench.web.support.TenantWebMvcFixtures
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class ProjectWorkItemControllerDirectTest :
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
    val searchPage =
      WorkItemSearchPage(
        result =
          WorkItemSearchResult(
            hits =
              listOf(
                WorkItemSearchHit(
                  apiId = "iss_01JABCDEFGHJKMNPQRSTVWXYZ0",
                  key = "CORE-1",
                  title = "Fix login",
                  description = null,
                  projectApiId = TenantWebMvcFixtures.PROJECT_PUBLIC_ID,
                  issueTypeApiId = "typ_01JABCDEFGHJKMNPQRSTVWXYZ0",
                  issueTypeConfigApiId = "itc_01JABCDEFGHJKMNPQRSTVWXYZ0",
                  statusApiId = "sts_01JABCDEFGHJKMNPQRSTVWXYZ0",
                  statusGroup = "todo",
                  priorityApiId = null,
                  reporterApiId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
                  assigneeApiId = null,
                  sprintApiId = null,
                  createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
                  updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
                  properties = JsonObject(emptyMap()),
                )
              ),
            total = 1,
          ),
        page = WorkItemSearchPageInfo(limit = 25, offset = 10, nextOffset = null),
      )

    "search delegates to query service with parsed request" {
      coEvery { queryService.search(any(), any(), any()) } returns searchPage

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
            offset = 10,
          ),
          projectContext,
        )
      }

      response.page.limit shouldBe 25
      response.result.hits.single().key shouldBe "CORE-1"
      coVerify {
        queryService.search(
          scope = WorkItemSearchScope(TenantWebMvcFixtures.TENANT_ID, TenantWebMvcFixtures.PROJECT_ID),
          query = any(),
          page = WorkItemSearchPageRequest(limit = 25, offset = 10),
        )
      }
    }
  })
