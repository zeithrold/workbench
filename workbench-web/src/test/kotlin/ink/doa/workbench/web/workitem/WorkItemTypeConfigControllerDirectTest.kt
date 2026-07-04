package ink.doa.workbench.web.workitem

import com.fasterxml.jackson.databind.ObjectMapper
import ink.doa.workbench.agile.project.ProjectService
import ink.doa.workbench.agile.workitem.IssueTypeConfigService
import ink.doa.workbench.core.common.context.ApiVersion
import ink.doa.workbench.core.common.context.InstanceContextSummary
import ink.doa.workbench.core.common.context.TenantContextSummary
import ink.doa.workbench.core.common.context.TenantRequestContext
import ink.doa.workbench.core.common.context.UserContextSummary
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.web.support.TenantWebMvcFixtures
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class WorkItemTypeConfigControllerDirectTest :
  StringSpec({
    val mapper = ObjectMapper()
    val configs = mockk<IssueTypeConfigService>()
    val projects = mockk<ProjectService>()
    val controller = WorkItemTypeConfigController(configs, projects)
    val tenantContext =
      TenantRequestContext(
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
      )
    val sampleConfig =
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

    "create delegates to issue type config service" {
      coEvery { configs.create(any()) } returns
        IssueTypeConfigDetails(
          config = sampleConfig,
          statuses = emptyList(),
          properties = emptyList(),
        )

      val response = runBlocking {
        controller.create(
          CreateIssueTypeConfigRequest(
            scope = "tenant",
            projectId = null,
            issueTypeId = "typ_01JABCDEFGHJKMNPQRSTVWXYZ0",
            workflowId = "wfl_01JABCDEFGHJKMNPQRSTVWXYZ0",
            rank = 50,
            createFields =
              mapper.readTree(
                """
                  {
                    "version": 1,
                    "resource": "work_item",
                    "target": "create",
                    "fields": {}
                  }
                  """
              ),
            statuses = listOf(TypeConfigStatusRequest("sts_01JABCDEFGHJKMNPQRSTVWXYZ0", true)),
            properties =
              listOf(
                TypeConfigPropertyRequest(
                  propertyId = "fld_01JABCDEFGHJKMNPQRSTVWXYZ0",
                  validationOverride = mapper.readTree("""{"required": true}"""),
                  rank = 1,
                  displayConfig = mapper.readTree("""{"label": "Points"}"""),
                )
              ),
          ),
          tenantContext,
        )
      }

      response.issueTypeId shouldBe "typ_01JABCDEFGHJKMNPQRSTVWXYZ0"
    }

    "create resolves project id for project scoped configs" {
      coEvery {
        projects.get(TenantWebMvcFixtures.TENANT_ID, TenantWebMvcFixtures.PROJECT_PUBLIC_ID)
      } returns TenantWebMvcFixtures.PROJECT_RECORD
      coEvery { configs.create(any()) } returns
        IssueTypeConfigDetails(
          config = sampleConfig.copy(scope = WorkItemConfigScope.PROJECT),
          statuses = emptyList(),
          properties = emptyList(),
        )

      val response = runBlocking {
        controller.create(
          CreateIssueTypeConfigRequest(
            scope = "project",
            projectId = TenantWebMvcFixtures.PROJECT_PUBLIC_ID,
            issueTypeId = "typ_01JABCDEFGHJKMNPQRSTVWXYZ0",
            workflowId = "wfl_01JABCDEFGHJKMNPQRSTVWXYZ0",
            createFields =
              mapper.readTree(
                """{"version":1,"resource":"work_item","target":"create","fields":{}}"""
              ),
            statuses = listOf(TypeConfigStatusRequest("sts_01JABCDEFGHJKMNPQRSTVWXYZ0")),
          ),
          tenantContext,
        )
      }

      response.issueTypeId shouldBe "typ_01JABCDEFGHJKMNPQRSTVWXYZ0"
    }
  })
