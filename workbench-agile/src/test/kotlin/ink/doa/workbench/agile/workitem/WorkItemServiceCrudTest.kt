package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import ink.doa.workbench.core.workitem.IssueSubtypeConstraintRepository
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.model.DeleteWorkItemCommand
import ink.doa.workbench.core.workitem.model.EffectiveIssueTypeConfig
import ink.doa.workbench.core.workitem.model.IssueSubtypeConstraintRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigStatusRecord
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
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

    "availableCreateForm returns editable field metadata" {
      val repository = mockk<WorkItemRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val record = workItem(tenantId, projectId)
      val config = configDetailsWithInitialStatus(tenantId, record.issueTypeConfigApiId)
      val effective = EffectiveIssueTypeConfig(config, WorkItemConfigScope.TENANT)
      coEvery { configs.resolveEffective(tenantId, projectId, "typ_task") } returns effective
      coEvery { repository.resolveUserApiId(actorId) } returns PublicId.new("usr")
      coEvery { repository.resolveProjectApiId(tenantId, projectId) } returns PublicId.new("prj")
      val service = workItemService(repository, configs)

      val form = service.availableCreateForm(tenantId, projectId, "typ_task", actorId)

      form.initialStatusId shouldBe config.statuses.single().statusApiId
      form.editableFields.shouldContain("title")
      form.fieldMeta.shouldContain(form.fieldMeta.single { it.path == "title" })
    }

    "create throws when effective config is missing" {
      val repository = mockk<WorkItemRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      coEvery { configs.resolveEffective(tenantId, projectId, "missing") } returns null
      val service = workItemService(repository, configs)

      shouldThrow<ResourceNotFoundException> {
        service.create(
          ink.doa.workbench.core.workitem.model.CreateWorkItemCommand(
            tenantId = tenantId,
            projectId = projectId,
            issueTypeApiId = "missing",
            title = "Task",
            description = null,
            reporterId = actorId,
            actorUserId = actorId,
          )
        )
      }
    }

    "create throws when initial status is missing" {
      val repository = mockk<WorkItemRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val config = configDetails(tenantId, PublicId.new("itc"))
      val effective = EffectiveIssueTypeConfig(config, WorkItemConfigScope.TENANT)
      coEvery { configs.resolveEffective(tenantId, projectId, "typ_task") } returns effective
      val service = workItemService(repository, configs)

      shouldThrow<InvalidRequestException> {
        service.create(
          ink.doa.workbench.core.workitem.model.CreateWorkItemCommand(
            tenantId = tenantId,
            projectId = projectId,
            issueTypeApiId = "typ_task",
            title = "Task",
            description = null,
            reporterId = actorId,
            actorUserId = actorId,
          )
        )
      }
    }

    "create rejects root work item for child-only type" {
      val repository = mockk<WorkItemRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val subtypeConstraints = mockk<IssueSubtypeConstraintRepository>()
      val config = configDetailsWithInitialStatus(tenantId, PublicId.new("itc"))
      coEvery { configs.resolveEffective(tenantId, projectId, "typ_task") } returns
        EffectiveIssueTypeConfig(config, WorkItemConfigScope.TENANT)
      coEvery {
        subtypeConstraints.isChildOnlyType(tenantId, projectId, config.config.issueTypeId)
      } returns true
      val service = workItemService(repository, configs, subtypeConstraints = subtypeConstraints)

      shouldThrow<InvalidRequestException> {
        service.create(
          ink.doa.workbench.core.workitem.model.CreateWorkItemCommand(
            tenantId = tenantId,
            projectId = projectId,
            issueTypeApiId = "typ_task",
            title = "Task",
            description = null,
            reporterId = actorId,
            actorUserId = actorId,
          )
        )
      }
    }

    "create child validates allowed subtype and passes parent id to repository" {
      val repository = mockk<WorkItemRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val subtypeConstraints = mockk<IssueSubtypeConstraintRepository>()
      val config = configDetailsWithInitialStatus(tenantId, PublicId.new("itc"))
      val parent = workItem(tenantId, projectId)
      val created = WorkItemMutationResult(workItem(tenantId, projectId), "work_item.created")

      coEvery { configs.resolveEffective(tenantId, projectId, "typ_task") } returns
        EffectiveIssueTypeConfig(config, WorkItemConfigScope.TENANT)
      coEvery { repository.findByApiId(tenantId, parent.apiId.value) } returns parent
      coEvery {
        subtypeConstraints.findAllowedChildType(
          tenantId,
          projectId,
          parent.issueTypeId,
          config.config.issueTypeId,
        )
      } returns subtypeConstraint(tenantId, parent.issueTypeId, config.config.issueTypeId)
      coEvery { repository.resolveUserApiId(actorId) } returns PublicId.new("usr")
      coEvery { repository.resolveProjectApiId(tenantId, projectId) } returns PublicId.new("prj")
      coEvery { repository.create(any(), any(), any(), any(), any(), parent.id) } returns created
      val service = workItemService(repository, configs, subtypeConstraints = subtypeConstraints)

      service.create(
        ink.doa.workbench.core.workitem.model.CreateWorkItemCommand(
          tenantId = tenantId,
          projectId = projectId,
          issueTypeApiId = "typ_task",
          title = "Task",
          description = null,
          reporterId = actorId,
          actorUserId = actorId,
          parentWorkItemApiId = parent.apiId.value,
        )
      )

      coVerify { repository.create(any(), any(), any(), any(), any(), parent.id) }
    }

    "create throws when parent work item is missing" {
      val repository = mockk<WorkItemRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val config = configDetailsWithInitialStatus(tenantId, PublicId.new("itc"))
      coEvery { configs.resolveEffective(tenantId, projectId, "typ_task") } returns
        EffectiveIssueTypeConfig(config, WorkItemConfigScope.TENANT)
      coEvery { repository.findByApiId(tenantId, "missing-parent") } returns null
      val service = workItemService(repository, configs)

      shouldThrow<ResourceNotFoundException> {
        service.create(
          ink.doa.workbench.core.workitem.model.CreateWorkItemCommand(
            tenantId = tenantId,
            projectId = projectId,
            issueTypeApiId = "typ_task",
            title = "Task",
            description = null,
            reporterId = actorId,
            actorUserId = actorId,
            parentWorkItemApiId = "missing-parent",
          )
        )
      }
    }

    "create rejects child when parent is in another project" {
      val repository = mockk<WorkItemRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val subtypeConstraints = mockk<IssueSubtypeConstraintRepository>()
      val config = configDetailsWithInitialStatus(tenantId, PublicId.new("itc"))
      val parent = workItem(tenantId, UUID.randomUUID())
      coEvery { configs.resolveEffective(tenantId, projectId, "typ_task") } returns
        EffectiveIssueTypeConfig(config, WorkItemConfigScope.TENANT)
      coEvery { repository.findByApiId(tenantId, parent.apiId.value) } returns parent
      val service = workItemService(repository, configs, subtypeConstraints = subtypeConstraints)

      shouldThrow<InvalidRequestException> {
        service.create(
          ink.doa.workbench.core.workitem.model.CreateWorkItemCommand(
            tenantId = tenantId,
            projectId = projectId,
            issueTypeApiId = "typ_task",
            title = "Task",
            description = null,
            reporterId = actorId,
            actorUserId = actorId,
            parentWorkItemApiId = parent.apiId.value,
          )
        )
      }
    }

    "create rejects disallowed child subtype" {
      val repository = mockk<WorkItemRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val subtypeConstraints = mockk<IssueSubtypeConstraintRepository>()
      val config = configDetailsWithInitialStatus(tenantId, PublicId.new("itc"))
      val parent = workItem(tenantId, projectId)
      coEvery { configs.resolveEffective(tenantId, projectId, "typ_task") } returns
        EffectiveIssueTypeConfig(config, WorkItemConfigScope.TENANT)
      coEvery { repository.findByApiId(tenantId, parent.apiId.value) } returns parent
      coEvery {
        subtypeConstraints.findAllowedChildType(
          tenantId,
          projectId,
          parent.issueTypeId,
          config.config.issueTypeId,
        )
      } returns null
      val service = workItemService(repository, configs, subtypeConstraints = subtypeConstraints)

      shouldThrow<InvalidRequestException> {
        service.create(
          ink.doa.workbench.core.workitem.model.CreateWorkItemCommand(
            tenantId = tenantId,
            projectId = projectId,
            issueTypeApiId = "typ_task",
            title = "Task",
            description = null,
            reporterId = actorId,
            actorUserId = actorId,
            parentWorkItemApiId = parent.apiId.value,
          )
        )
      }
    }
  })

private fun workItemService(
  repository: WorkItemRepository,
  configs: IssueTypeConfigRepository = mockk(relaxed = true),
  events: DomainEventPublisher = mockk(relaxed = true),
  subtypeConstraints: IssueSubtypeConstraintRepository? = null,
): WorkItemService {
  val effectiveSubtypeConstraints =
    subtypeConstraints
      ?: mockk<IssueSubtypeConstraintRepository>().also {
        coEvery { it.isChildOnlyType(any(), any(), any()) } returns false
      }
  val fieldPermissions = mockk<WorkItemFieldPermissionService>()
  coEvery { fieldPermissions.canWriteField(any(), any()) } returns true
  coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } returns true
  val mutationSupport = WorkItemMutationSupport(repository, configs, events)
  val descriptionAttachmentValidator = mockk<WorkItemDescriptionAttachmentValidator>()
  coEvery { descriptionAttachmentValidator.rejectCreateDescriptionReferences(any()) } returns Unit
  coEvery {
    descriptionAttachmentValidator.validateReferences(any(), any(), any(), any(), any())
  } returns Unit
  return WorkItemService(
    repository = repository,
    configs = configs,
    subtypeConstraints = effectiveSubtypeConstraints,
    mutationSupport = mutationSupport,
    fieldMutationReconciler =
      WorkItemFieldMutationReconciler(
        fieldPermissions,
        Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC),
      ),
    fieldPermissions = fieldPermissions,
    descriptionAttachmentValidator = descriptionAttachmentValidator,
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

private fun configDetailsWithInitialStatus(
  tenantId: UUID,
  configApiId: PublicId,
): IssueTypeConfigDetails {
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  val configId = UUID.randomUUID()
  val statusId = UUID.randomUUID()
  return IssueTypeConfigDetails(
    config =
      IssueTypeConfigRecord(
        id = configId,
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
        createFields =
          kotlinx.serialization.json.Json.parseToJsonElement(
              """
              {
                "version": 1,
                "resource": "work_item",
                "target": "create",
                "fields": {
                  "title": { "participation": "required" }
                }
              }
              """
                .trimIndent()
            )
            .let { it as JsonObject },
      ),
    statuses =
      listOf(
        IssueTypeConfigStatusRecord(
          id = UUID.randomUUID(),
          tenantId = tenantId,
          issueTypeConfigId = configId,
          statusId = statusId,
          statusApiId = PublicId.new("sts"),
          code = "todo",
          name = "Todo",
          statusGroup = WorkItemStatusGroup.TODO,
          isInitial = true,
          isTerminal = false,
          rank = 100,
        )
      ),
    properties = emptyList(),
  )
}
