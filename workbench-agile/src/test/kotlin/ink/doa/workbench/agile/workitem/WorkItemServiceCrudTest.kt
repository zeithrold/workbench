package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.model.DeleteWorkItemCommand
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.justRun
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class WorkItemServiceCrudTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val actorId = UUID.randomUUID()

    "get returns work item from repository" {
      val repository = mockk<WorkItemRepository>()
      val record = workItem(tenantId, projectId)
      coEvery { repository.findByApiId(tenantId, projectId, record.apiId.value) } returns record
      val service = workItemService(repository)

      service.get(tenantId, projectId, record.apiId.value).key shouldBe record.key
    }

    "get throws when work item is missing" {
      val repository = mockk<WorkItemRepository>()
      coEvery { repository.findByApiId(tenantId, projectId, "missing") } returns null
      val service = workItemService(repository)

      shouldThrow<ResourceNotFoundException> { service.get(tenantId, projectId, "missing") }
    }

    "list delegates to repository" {
      val repository = mockk<WorkItemRepository>()
      val record = workItem(tenantId, projectId)
      coEvery { repository.listByProject(tenantId, projectId, 25, 10) } returns listOf(record)
      val service = workItemService(repository)

      service.list(tenantId, projectId, 25, 10).single().apiId shouldBe record.apiId
    }

    "update publishes mutation result" {
      val repository = mockk<WorkItemRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val events = mockk<DomainEventPublisher>()
      justRun { events.publish<Any>(any(), any(), any(), any()) }
      val record = workItem(tenantId, projectId)
      val config = configDetails(tenantId, record.issueTypeConfigApiId)
      val updated = WorkItemMutationResult(record.copy(title = "Updated"), "work_item.updated")

      coEvery { repository.findByApiId(tenantId, projectId, record.apiId.value) } returns record
      coEvery { configs.findConfig(tenantId, record.issueTypeConfigApiId.value) } returns config
      coEvery { repository.update(any(), any()) } returns updated

      val service = workItemService(repository, configs, events)
      val result =
        service.update(
          UpdateWorkItemCommand(
            tenantId = tenantId,
            projectId = projectId,
            workItemApiId = record.apiId.value,
            title = "Updated",
            actorUserId = actorId,
          )
        )

      result.workItem.title shouldBe "Updated"
      coVerify { repository.update(any(), any()) }
    }

    "delete soft deletes and publishes event" {
      val repository = mockk<WorkItemRepository>()
      val events = mockk<DomainEventPublisher>()
      justRun { events.publish<Any>(any(), any(), any(), any()) }
      val record = workItem(tenantId, projectId)
      val deleted = WorkItemMutationResult(record, "work_item.updated")

      coEvery { repository.softDelete(any()) } returns deleted
      val service = workItemService(repository, events = events)

      service
        .delete(
          DeleteWorkItemCommand(
            tenantId = tenantId,
            projectId = projectId,
            workItemApiId = record.apiId.value,
            actorUserId = actorId,
          )
        )
        .workItem shouldBe record

      coVerify { repository.softDelete(any()) }
    }
  })

private fun workItemService(
  repository: WorkItemRepository,
  configs: IssueTypeConfigRepository = mockk(relaxed = true),
  events: DomainEventPublisher = mockk(relaxed = true),
): WorkItemService {
  val fieldPermissions = mockk<WorkItemFieldPermissionService>()
  coEvery { fieldPermissions.canWriteField(any(), any()) } returns true
  val mutationSupport = WorkItemMutationSupport(repository, configs, events)
  return WorkItemService(
    repository = repository,
    configs = configs,
    mutationSupport = mutationSupport,
    fieldMutationReconciler =
      WorkItemFieldMutationReconciler(
        fieldPermissions,
        Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC),
      ),
    fieldPermissions = fieldPermissions,
  )
}

private fun workItem(tenantId: UUID, projectId: UUID): WorkItemRecord {
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  return WorkItemRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("wki"),
    tenantId = tenantId,
    projectId = projectId,
    issueTypeApiId = PublicId.new("typ"),
    issueTypeConfigApiId = PublicId.new("itc"),
    key = "PROJ-1",
    title = "Task",
    description = null,
    statusId = UUID.randomUUID(),
    statusApiId = PublicId.new("sts"),
    statusGroup = WorkItemStatusGroup.TODO,
    reporterId = UUID.randomUUID(),
    assigneeId = null,
    priorityApiId = null,
    reporterApiId = PublicId.new("usr"),
    assigneeApiId = null,
    sprintApiId = null,
    properties = JsonObject(emptyMap()),
    createdAt = now,
    updatedAt = now,
  )
}

private fun configDetails(tenantId: UUID, configApiId: PublicId): IssueTypeConfigDetails {
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  return IssueTypeConfigDetails(
    config =
      IssueTypeConfigRecord(
        id = UUID.randomUUID(),
        apiId = configApiId,
        tenantId = tenantId,
        scope = WorkItemConfigScope.TENANT,
        projectId = null,
        issueTypeId = UUID.randomUUID(),
        issueTypeApiId = PublicId.new("typ"),
        workflowId = UUID.randomUUID(),
        workflowApiId = PublicId.new("wfl"),
        version = 1,
        nameOverride = null,
        iconOverride = null,
        colorOverride = null,
        rank = 100,
        isActive = true,
        validFrom = now,
        validTo = null,
        createdBy = null,
        createdAt = now,
        updatedAt = now,
        createFields = JsonObject(emptyMap()),
      ),
    statuses = emptyList(),
    properties = emptyList(),
  )
}
