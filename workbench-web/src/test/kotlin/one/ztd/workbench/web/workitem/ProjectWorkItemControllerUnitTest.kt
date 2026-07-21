package one.ztd.workbench.web.workitem

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.agile.workitem.WorkItemDisplayFieldCatalogService
import one.ztd.workbench.agile.workitem.WorkItemDisplayFieldDefinition
import one.ztd.workbench.agile.workitem.WorkItemFieldOption
import one.ztd.workbench.agile.workitem.WorkItemFieldOptionPage
import one.ztd.workbench.agile.workitem.WorkItemFieldOptionService
import one.ztd.workbench.agile.workitem.WorkItemFieldOptionsPageRequest
import one.ztd.workbench.agile.workitem.WorkItemFieldOptionsRequest
import one.ztd.workbench.agile.workitem.WorkItemQueryService
import one.ztd.workbench.agile.workitem.WorkItemSearchActor
import one.ztd.workbench.agile.workitem.WorkItemSearchPageRequest
import one.ztd.workbench.agile.workitem.WorkItemSearchScope
import one.ztd.workbench.agile.workitem.WorkItemService
import one.ztd.workbench.agile.workitem.WorkItemTransitionService
import one.ztd.workbench.agile.workitem.model.WorkItemFieldCapability
import one.ztd.workbench.agile.workitem.model.WorkItemFieldCapabilityState
import one.ztd.workbench.agile.workitem.model.WorkItemIssueTypeSummary
import one.ztd.workbench.agile.workitem.model.WorkItemSearchHit
import one.ztd.workbench.agile.workitem.model.WorkItemSearchResult
import one.ztd.workbench.agile.workitem.model.WorkItemStatusSummary
import one.ztd.workbench.agile.workitem.model.WorkItemUserSummary
import one.ztd.workbench.agile.workitem.query.WorkItemSearchGroupScope
import one.ztd.workbench.kernel.common.context.InstanceContextSummary
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.kernel.common.pagination.WorkItemSearchCursor
import one.ztd.workbench.web.api.AuthenticatedOnly
import one.ztd.workbench.web.api.Authorize
import one.ztd.workbench.web.api.context.ApiVersion
import one.ztd.workbench.web.api.context.ProjectContextSummary
import one.ztd.workbench.web.api.context.ProjectRequestContext
import one.ztd.workbench.web.api.context.TenantContextSummary
import one.ztd.workbench.web.api.context.UserContextSummary
import one.ztd.workbench.web.api.http.WORKBENCH_NEXT_CURSOR_HEADER
import one.ztd.workbench.web.support.TenantWebMvcFixtures
import tools.jackson.databind.ObjectMapper

class ProjectWorkItemControllerUnitTest :
  StringSpec({
    val mapper = ObjectMapper()
    val service = mockk<WorkItemService>(relaxed = true)
    val transitionService = mockk<WorkItemTransitionService>(relaxed = true)
    val queryService = mockk<WorkItemQueryService>()
    val displayFieldCatalog = mockk<WorkItemDisplayFieldCatalogService>()
    val fieldOptions = mockk<WorkItemFieldOptionService>()
    val controller =
      ProjectWorkItemController(
        service,
        transitionService,
        queryService,
        displayFieldCatalog,
        fieldOptions,
      )
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

    "PATCH uses authenticated-only security and delegates field authorization to the service" {
      val method =
        ProjectWorkItemController::class.java.declaredMethods.single { it.name == "update" }

      method.isAnnotationPresent(AuthenticatedOnly::class.java) shouldBe true
      method.isAnnotationPresent(Authorize::class.java) shouldBe false
    }
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
              fieldCapabilities =
                mapOf(
                  "assignee" to
                    WorkItemFieldCapability(
                      WorkItemFieldCapabilityState.READ_ONLY,
                      "permission_denied",
                    )
                ),
            )
          ),
        nextCursor = nextCursor,
      )

    "search delegates to query service with parsed request and cursor header" {
      coEvery { queryService.search(any(), any(), any(), any(), any()) } returns searchResult

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
      response.body?.single()?.fieldCapabilities?.get("assignee")?.reason shouldBe
        "permission_denied"
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
          actor =
            WorkItemSearchActor(
              TenantWebMvcFixtures.USER_ID,
              TenantWebMvcFixtures.PRINCIPAL.user.apiId.value,
            ),
        )
      }
    }

    "display field catalog is mapped to the explicit response contract" {
      coEvery { displayFieldCatalog.list(any(), any()) } returns
        listOf(
          WorkItemDisplayFieldDefinition(
            key = "property.points",
            name = "Story points",
            dataType = "number",
            propertyId = "fld_points",
            validation = JsonObject(mapOf("minimum" to JsonPrimitive(0))),
          )
        )

      val response = runBlocking { controller.displayFields(projectContext) }.single()

      response.key shouldBe "property.points"
      response.propertyId shouldBe "fld_points"
      response.validation shouldBe JsonObject(mapOf("minimum" to JsonPrimitive(0)))
    }

    "field options preserve option metadata and the next cursor header" {
      coEvery { fieldOptions.list(any()) } returns
        WorkItemFieldOptionPage(
          items =
            listOf(
              WorkItemFieldOption(
                id = "usr_jordan",
                label = "Jordan",
                description = "jordan@example.com",
                color = "#64748b",
                icon = "user",
                status = "active",
              )
            ),
          nextCursor = "next-options",
        )

      val response = runBlocking {
        controller.fieldOptions(
          "iss_1",
          "assignee",
          WorkItemFieldOptionsParameters(query = "jor", cursor = "cursor", limit = 10),
          projectContext,
        )
      }

      response.headers.getFirst(WORKBENCH_NEXT_CURSOR_HEADER) shouldBe "next-options"
      response.body?.single()?.label shouldBe "Jordan"
      response.body?.single()?.status shouldBe "active"
      coVerify {
        fieldOptions.list(
          WorkItemFieldOptionsRequest(
            tenantId = TenantWebMvcFixtures.TENANT_ID,
            projectId = TenantWebMvcFixtures.PROJECT_ID,
            workItemApiId = "iss_1",
            fieldKey = "assignee",
            actorUserId = TenantWebMvcFixtures.USER_ID,
            page = WorkItemFieldOptionsPageRequest("jor", "cursor", 10),
          )
        )
      }
    }
  })
