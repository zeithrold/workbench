package one.ztd.workbench.agile.workitem

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.agile.workitem.model.CreateIssueSubtypeConstraintCommand
import one.ztd.workbench.agile.workitem.model.IssueSubtypeConstraintRecord
import one.ztd.workbench.agile.workitem.model.IssueTypeRecord
import one.ztd.workbench.agile.workitem.model.WorkItemConfigScope
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.ids.PublicId

class IssueSubtypeConstraintServiceTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val actorId = UUID.randomUUID()

    "create validates parent and child issue types before delegating to repository" {
      val repository = mockk<IssueSubtypeConstraintRepository>()
      val catalog = mockk<WorkItemCatalogRepository>()
      val parentType = issueType(tenantId, PublicId.new("typ"))
      val childType = issueType(tenantId, PublicId.new("typ"))
      val created = subtypeConstraint(tenantId, parentType.id, childType.id)
      val command =
        CreateIssueSubtypeConstraintCommand(
          tenantId = tenantId,
          projectId = projectId,
          parentIssueTypeApiId = parentType.apiId.value,
          childIssueTypeApiId = childType.apiId.value,
          createdBy = actorId,
        )

      coEvery { catalog.findIssueType(tenantId, parentType.apiId.value, projectId) } returns
        parentType
      coEvery { catalog.findIssueType(tenantId, childType.apiId.value, projectId) } returns
        childType
      coEvery { repository.create(command) } returns created

      val service = IssueSubtypeConstraintService(repository, catalog)
      service.create(command) shouldBe created

      coVerify { repository.create(command) }
    }

    "create rejects missing parent issue type" {
      val repository = mockk<IssueSubtypeConstraintRepository>(relaxed = true)
      val catalog = mockk<WorkItemCatalogRepository>()
      coEvery { catalog.findIssueType(tenantId, "missing", projectId) } returns null
      val service = IssueSubtypeConstraintService(repository, catalog)

      shouldThrow<ResourceNotFoundException> {
        service.create(
          CreateIssueSubtypeConstraintCommand(
            tenantId = tenantId,
            projectId = projectId,
            parentIssueTypeApiId = "missing",
            childIssueTypeApiId = "typ_task",
          )
        )
      }
    }

    "list delegates to repository" {
      val repository = mockk<IssueSubtypeConstraintRepository>()
      val catalog = mockk<WorkItemCatalogRepository>()
      val record = subtypeConstraint(tenantId, UUID.randomUUID(), UUID.randomUUID())
      coEvery { repository.list(tenantId, projectId) } returns listOf(record)
      val service = IssueSubtypeConstraintService(repository, catalog)

      service.list(tenantId, projectId).single() shouldBe record
      coVerify { repository.list(tenantId, projectId) }
    }

    "deactivate delegates to repository" {
      val repository = mockk<IssueSubtypeConstraintRepository>()
      val catalog = mockk<WorkItemCatalogRepository>()
      val constraintId = UUID.randomUUID()
      val record = subtypeConstraint(tenantId, UUID.randomUUID(), UUID.randomUUID())
      coEvery { repository.deactivate(tenantId, constraintId, actorId) } returns record
      val service = IssueSubtypeConstraintService(repository, catalog)

      service.deactivate(tenantId, constraintId, actorId) shouldBe record
      coVerify { repository.deactivate(tenantId, constraintId, actorId) }
    }
  })

private fun issueType(tenantId: UUID, apiId: PublicId): IssueTypeRecord {
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  return IssueTypeRecord(
    id = UUID.randomUUID(),
    apiId = apiId,
    tenantId = tenantId,
    scope = WorkItemConfigScope.TENANT,
    projectId = null,
    code = "epic",
    name = "Epic",
    description = null,
    icon = null,
    color = null,
    rank = 100,
    isActive = true,
    createdAt = now,
    updatedAt = now,
  )
}

private fun subtypeConstraint(
  tenantId: UUID,
  parentIssueTypeId: UUID,
  childIssueTypeId: UUID,
): IssueSubtypeConstraintRecord =
  IssueSubtypeConstraintRecord(
    id = UUID.randomUUID(),
    tenantId = tenantId,
    projectId = null,
    parentIssueTypeId = parentIssueTypeId,
    parentIssueTypeApiId = PublicId.new("typ"),
    childIssueTypeId = childIssueTypeId,
    childIssueTypeApiId = PublicId.new("typ"),
    isDefault = false,
    minChildren = null,
    maxChildren = null,
    isActive = true,
    createdBy = null,
    createdAt = OffsetDateTime.now(ZoneOffset.UTC),
    updatedAt = OffsetDateTime.now(ZoneOffset.UTC),
  )
