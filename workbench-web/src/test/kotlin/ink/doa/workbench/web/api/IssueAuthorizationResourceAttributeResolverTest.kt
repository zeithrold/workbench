package ink.doa.workbench.web.api

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.model.CredentialType
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.AuthorizationEnvironment
import ink.doa.workbench.core.permission.model.AuthorizationRequest
import ink.doa.workbench.core.permission.model.AuthorizationResource
import ink.doa.workbench.core.permission.model.AuthorizationScope
import ink.doa.workbench.core.permission.model.AuthorizationSubject
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
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
    val userId = UUID.randomUUID()
    val workItems = mockk<WorkItemRepository>()
    val resolver = IssueAuthorizationResourceAttributeResolver(workItems)

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
          reporterId = userId,
          assigneeId = userId,
          priorityApiId = null,
          reporterApiId = PublicId.new("usr"),
          assigneeApiId = PublicId.new("usr"),
          sprintApiId = null,
          properties = JsonObject(emptyMap()),
          createdAt = OffsetDateTime.now(ZoneOffset.UTC),
          updatedAt = OffsetDateTime.now(ZoneOffset.UTC),
        )
      coEvery { workItems.findByApiId(tenantId, projectId, issue.apiId.value) } returns issue

      val attributes = runBlocking {
        resolver.resolveAttributes(
          AuthorizationRequest(
            scope = AuthorizationScope.TENANT,
            subject =
              AuthorizationSubject(
                userId = userId,
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

      attributes shouldContain ("reporter" to userId.toString())
      attributes shouldContain ("assignee" to userId.toString())
      attributes shouldContain ("statusGroup" to "todo")
      attributes shouldContain ("project" to projectId.toString())
    }
  })
