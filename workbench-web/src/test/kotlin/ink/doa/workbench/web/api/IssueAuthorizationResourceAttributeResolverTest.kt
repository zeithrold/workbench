package ink.doa.workbench.web.api

import ink.doa.workbench.agile.project.ProjectRepository
import ink.doa.workbench.agile.project.model.ProjectRecord
import ink.doa.workbench.agile.project.model.ProjectStatus
import ink.doa.workbench.agile.workitem.WorkItemRepository
import ink.doa.workbench.agile.workitem.model.WorkItemRecord
import ink.doa.workbench.agile.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.identity.model.CredentialType
import ink.doa.workbench.identity.permission.model.AuthorizationAction
import ink.doa.workbench.identity.permission.model.AuthorizationEnvironment
import ink.doa.workbench.identity.permission.model.AuthorizationRequest
import ink.doa.workbench.identity.permission.model.AuthorizationResource
import ink.doa.workbench.identity.permission.model.AuthorizationScope
import ink.doa.workbench.identity.permission.model.AuthorizationSubject
import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class IssueAuthorizationResourceAttributeResolverTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val projectApiId = PublicId.new("prj")
    val reporterApiId = PublicId.new("usr")
    val assigneeApiId = PublicId.new("usr")
    val workItems = mockk<WorkItemRepository>()
    val projects = mockk<ProjectRepository>(relaxed = true)
    val resolver = IssueAuthorizationResourceAttributeResolver(workItems, projects)

    "supports issue resources with id" {
      resolver.supports(
        AuthorizationResource(
          type = "issue",
          id = "iss_01",
          tenantId = tenantId,
          projectId = projectId,
        )
      ) shouldBe true
      resolver.supports(AuthorizationResource(type = "project", id = "prj_01")) shouldBe false
    }

    "loads issue attributes for authorization" {
      val issue =
        WorkItemRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("iss"),
          tenantId = tenantId,
          projectId = projectId,
          issueTypeApiId = PublicId.new("ity"),
          issueTypeConfigApiId = PublicId.new("itc"),
          key = "CORE-1",
          title = "Issue",
          description = null,
          statusId = UUID.randomUUID(),
          statusApiId = PublicId.new("sts"),
          statusGroup = WorkItemStatusGroup.TODO,
          reporterId = UUID.randomUUID(),
          assigneeId = UUID.randomUUID(),
          priorityApiId = null,
          reporterApiId = reporterApiId,
          assigneeApiId = assigneeApiId,
          sprintApiId = null,
          properties = JsonObject(emptyMap()),
          createdAt = OffsetDateTime.now(ZoneOffset.UTC),
          updatedAt = OffsetDateTime.now(ZoneOffset.UTC),
        )
      coEvery { workItems.findByApiId(tenantId, projectId, issue.apiId.value) } returns issue
      coEvery { projects.findById(tenantId, projectId) } returns
        ProjectRecord(
          id = projectId,
          apiId = projectApiId,
          tenantId = tenantId,
          identifier = "CORE",
          name = "Core",
          description = null,
          status = ProjectStatus.ACTIVE,
        )

      val attributes = runBlocking {
        resolver.resolveAttributes(
          AuthorizationRequest(
            scope = AuthorizationScope.TENANT,
            subject =
              AuthorizationSubject(
                userId = UUID.randomUUID(),
                userApiId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
                loginAccountId = null,
                credentialType = CredentialType.SESSION,
                credentialId = null,
                credentialTenantId = tenantId,
                credentialScopes = emptySet(),
              ),
            tenantId = tenantId,
            action = AuthorizationAction("issue.view"),
            resource =
              AuthorizationResource(
                type = "issue",
                id = issue.apiId.value,
                tenantId = tenantId,
                projectId = projectId,
              ),
            environment = AuthorizationEnvironment(requestId = "req", occurredAt = Instant.now()),
          )
        )
      }

      attributes shouldContain ("reporter" to reporterApiId.value)
      attributes shouldContain ("assignee" to assigneeApiId.value)
      attributes shouldContain ("statusGroup" to "todo")
      attributes shouldContain ("project" to projectApiId.value)
    }
  })
