package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.PermissionDeniedException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.WorkflowConfigurationRepository
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.EffectiveIssueTypeConfig
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigPropertyRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigStatusRecord
import ink.doa.workbench.core.workitem.model.TransitionWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
import ink.doa.workbench.core.workitem.template.CreateFieldsLegacyMigrator
import ink.doa.workbench.core.workitem.template.CreateFieldsPropertyMigrationRow
import ink.doa.workbench.core.workitem.template.TemplateField
import ink.doa.workbench.core.workitem.template.TransitionFieldsLegacyMigrator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class WorkItemServiceTemplateDefaultsTest :
  StringSpec({
    val clock = Clock.fixed(Instant.parse("2026-07-04T10:15:30Z"), ZoneOffset.UTC)

    "applies create fields template defaults before repository create" {
      val repository = mockk<WorkItemRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val workflows = mockk<WorkflowConfigurationRepository>(relaxed = true)
      val events = mockk<DomainEventPublisher>()
      justRun { events.publish<Any>(any(), any(), any(), any()) }

      val actorId = UUID.randomUUID()
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val userApiId = PublicId.new("usr")
      val projectApiId = PublicId.new("prj")
      val config = config(defaultDueDate = true)
      val values = slot<List<WorkItemPropertyValue>>()
      val created = workItem(tenantId, projectId, config)

      coEvery { configs.resolveEffective(tenantId, projectId, "typ_task") } returns
        EffectiveIssueTypeConfig(config, WorkItemConfigScope.TENANT)
      coEvery { repository.resolveUserApiId(actorId) } returns userApiId
      coEvery { repository.resolveProjectApiId(tenantId, projectId) } returns projectApiId
      coEvery {
        repository.create(any(), any(), any(), any(), capture(values))
      } returns WorkItemMutationResult(created, "work_item.created")

      workItemService(repository, configs, workflows, events, clock)
        .create(
          CreateWorkItemCommand(
            tenantId = tenantId,
            projectId = projectId,
            issueTypeApiId = "typ_task",
            title = "Task",
            description = null,
            reporterId = actorId,
            actorUserId = actorId,
          )
        )

      values.captured.map { it.code to it.value } shouldContain
        ("dueDate" to JsonPrimitive("2026-07-07"))
    }

    "applies transition template defaults and lets request values override them" {
      val repository = mockk<WorkItemRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val workflows = mockk<WorkflowConfigurationRepository>()
      val events = mockk<DomainEventPublisher>()
      justRun { events.publish<Any>(any(), any(), any(), any()) }

      val actorId = UUID.randomUUID()
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val userApiId = PublicId.new("usr")
      val projectApiId = PublicId.new("prj")
      val config = config(defaultDueDate = false)
      val issue = workItem(tenantId, projectId, config)
      val values = slot<List<WorkItemPropertyValue>>()
      val command = slot<TransitionWorkItemCommand>()
      val transition = transition(config)

      coEvery { repository.findByApiId(tenantId, projectId, issue.apiId.value) } returns issue
      coEvery { configs.findConfig(tenantId, issue.issueTypeConfigApiId.value) } returns config
      coEvery { workflows.findTransition(tenantId, "trn_done") } returns transition
      coEvery { repository.listPropertyValues(tenantId, issue.id) } returns emptyMap()
      coEvery {
        repository.countChildrenNotInStatusGroups(tenantId, issue.id, setOf("done"))
      } returns 0
      coEvery { repository.resolveUserApiId(actorId) } returns userApiId
      coEvery { repository.resolveProjectApiId(tenantId, projectId) } returns projectApiId
      coEvery {
        repository.transition(capture(command), any(), any(), any(), capture(values))
      } returns WorkItemMutationResult(issue, "work_item.transitioned")

      workItemService(repository, configs, workflows, events, clock)
        .transition(
          TransitionWorkItemCommand(
            tenantId = tenantId,
            projectId = projectId,
            workItemApiId = issue.apiId.value,
            transitionApiId = "trn_done",
            actorUserId = actorId,
            properties = mapOf("resolution" to JsonPrimitive("wont_fix")),
          )
        )

      command.captured.assigneeApiId shouldBe userApiId.value
      values.captured.map { it.code to it.value } shouldContain
        ("resolution" to JsonPrimitive("wont_fix"))
      values.captured.map { it.code to it.value } shouldContain
        ("resolvedAt" to JsonPrimitive("2026-07-04T10:15:30Z"))
      coVerify(exactly = 1) { repository.transition(any(), any(), any(), any(), any()) }
    }

    "applies automatic create default when field write permission is denied and user omits field" {
      val repository = mockk<WorkItemRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val workflows = mockk<WorkflowConfigurationRepository>(relaxed = true)
      val events = mockk<DomainEventPublisher>()
      justRun { events.publish<Any>(any(), any(), any(), any()) }

      val actorId = UUID.randomUUID()
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val userApiId = PublicId.new("usr")
      val projectApiId = PublicId.new("prj")
      val config = config(defaultDueDate = true)
      val values = slot<List<WorkItemPropertyValue>>()
      val created = workItem(tenantId, projectId, config)
      val fieldPermissions = mockk<WorkItemFieldPermissionService>()

      coEvery { configs.resolveEffective(tenantId, projectId, "typ_task") } returns
        EffectiveIssueTypeConfig(config, WorkItemConfigScope.TENANT)
      coEvery { repository.resolveUserApiId(actorId) } returns userApiId
      coEvery { repository.resolveProjectApiId(tenantId, projectId) } returns projectApiId
      coEvery { fieldPermissions.canWriteField(any(), any()) } answers
        {
          secondArg<TemplateField>() is TemplateField.System
        }
      coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } answers
        {
          val field = secondArg<TemplateField>()
          field is TemplateField.System && field.canonicalName == "title"
        }
      coEvery {
        repository.create(any(), any(), any(), any(), capture(values))
      } returns WorkItemMutationResult(created, "work_item.created")

      WorkItemService(
          repository,
          configs,
          workflows,
          events,
          WorkItemFieldMutationReconciler(fieldPermissions, clock),
          fieldPermissions,
          mockk(relaxed = true),
          clock,
        )
        .create(
          CreateWorkItemCommand(
            tenantId = tenantId,
            projectId = projectId,
            issueTypeApiId = "typ_task",
            title = "Task",
            description = null,
            reporterId = actorId,
            actorUserId = actorId,
          )
        )

      values.captured.map { it.code to it.value } shouldContain
        ("dueDate" to JsonPrimitive("2026-07-07"))
    }

    "rejects create when automatic default field is submitted explicitly without permission" {
      val repository = mockk<WorkItemRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val workflows = mockk<WorkflowConfigurationRepository>(relaxed = true)
      val events = mockk<DomainEventPublisher>()
      val fieldPermissions = mockk<WorkItemFieldPermissionService>()

      val actorId = UUID.randomUUID()
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val userApiId = PublicId.new("usr")
      val projectApiId = PublicId.new("prj")
      val config = config(defaultDueDate = true)

      coEvery { configs.resolveEffective(tenantId, projectId, "typ_task") } returns
        EffectiveIssueTypeConfig(config, WorkItemConfigScope.TENANT)
      coEvery { repository.resolveUserApiId(actorId) } returns userApiId
      coEvery { repository.resolveProjectApiId(tenantId, projectId) } returns projectApiId
      coEvery { fieldPermissions.canWriteField(any(), any()) } answers
        {
          secondArg<TemplateField>() is TemplateField.System
        }
      coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } answers
        {
          val field = secondArg<TemplateField>()
          field is TemplateField.System && field.canonicalName == "title"
        }

      shouldThrow<PermissionDeniedException> {
          WorkItemService(
              repository,
              configs,
              workflows,
              events,
              WorkItemFieldMutationReconciler(fieldPermissions, clock),
              fieldPermissions,
              mockk(relaxed = true),
              clock,
            )
            .create(
              CreateWorkItemCommand(
                tenantId = tenantId,
                projectId = projectId,
                issueTypeApiId = "typ_task",
                title = "Task",
                description = null,
                reporterId = actorId,
                actorUserId = actorId,
                properties = mapOf("dueDate" to JsonPrimitive("2026-07-10")),
              )
            )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE
    }
  })

private fun config(defaultDueDate: Boolean): IssueTypeConfigDetails {
  val tenantId = UUID.randomUUID()
  val configId = UUID.randomUUID()
  val statusId = UUID.randomUUID()
  val workflowId = UUID.randomUUID()
  return IssueTypeConfigDetails(
    config =
      templateDefaultsIssueTypeConfig(
        tenantId = tenantId,
        configId = configId,
        workflowId = workflowId,
        defaultDueDate = defaultDueDate,
      ),
    statuses = listOf(templateDefaultsInitialStatus(tenantId, configId, statusId)),
    properties = templateDefaultsProperties(configId),
  )
}

private fun templateDefaultsIssueTypeConfig(
  tenantId: UUID,
  configId: UUID,
  workflowId: UUID,
  defaultDueDate: Boolean,
): IssueTypeConfigRecord {
  val dueDateDefault =
    Json.parseToJsonElement(
      """
      {
        "relativeDate": {
          "amount": 3,
          "unit": "day",
          "direction": "future",
          "anchor": "date.today"
        }
      }
      """
        .trimIndent()
    )
  return IssueTypeConfigRecord(
    id = configId,
    apiId = PublicId.new("itc"),
    tenantId = tenantId,
    scope = WorkItemConfigScope.TENANT,
    projectId = null,
    issueTypeId = UUID.randomUUID(),
    issueTypeApiId = PublicId.new("typ"),
    workflowId = workflowId,
    workflowApiId = PublicId.new("wfl"),
    version = 1,
    nameOverride = null,
    iconOverride = null,
    colorOverride = null,
    rank = 100,
    isActive = true,
    validFrom = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    validTo = null,
    createdBy = null,
    createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    createFields =
      CreateFieldsLegacyMigrator.migrate(
        listOf(
          CreateFieldsPropertyMigrationRow(
            code = "dueDate",
            isRequired = false,
            defaultValue = if (defaultDueDate) dueDateDefault else null,
          ),
          CreateFieldsPropertyMigrationRow(
            code = "resolvedAt",
            isRequired = false,
            defaultValue = null,
          ),
          CreateFieldsPropertyMigrationRow(
            code = "resolution",
            isRequired = false,
            defaultValue = null,
          ),
        )
      ),
  )
}

private fun templateDefaultsInitialStatus(
  tenantId: UUID,
  configId: UUID,
  statusId: UUID,
): IssueTypeConfigStatusRecord =
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

private fun templateDefaultsProperties(configId: UUID): List<IssueTypeConfigPropertyRecord> =
  listOf(
    property(configId, "dueDate", WorkItemPropertyDataType.DATE),
    property(configId, "resolvedAt", WorkItemPropertyDataType.DATETIME),
    property(configId, "resolution", WorkItemPropertyDataType.TEXT),
  )

private fun property(
  configId: UUID,
  code: String,
  type: WorkItemPropertyDataType,
): IssueTypeConfigPropertyRecord =
  IssueTypeConfigPropertyRecord(
    id = UUID.randomUUID(),
    tenantId = UUID.randomUUID(),
    issueTypeConfigId = configId,
    propertyId = UUID.randomUUID(),
    propertyApiId = PublicId.new("fld"),
    code = code,
    name = code,
    dataType = type,
    validationOverride = JsonObject(emptyMap()),
    rank = 100,
    displayConfig = JsonObject(emptyMap()),
  )

private fun transition(config: IssueTypeConfigDetails): WorkflowTransitionRecord =
  WorkflowTransitionRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("trn"),
    tenantId = config.config.tenantId,
    workflowId = config.config.workflowId,
    name = "Resolve",
    fromStatusId = config.statuses.single().statusId,
    fromStatusApiId = PublicId.new("sts"),
    toStatusId = config.statuses.single().statusId,
    toStatusApiId = PublicId.new("sts"),
    rank = 100,
    permissionCondition = JsonObject(emptyMap()),
    preconditionAst = JsonObject(emptyMap()),
    fields =
      TransitionFieldsLegacyMigrator.migrate(
        requiredProperties = Json.parseToJsonElement("""["resolution"]"""),
        optionalProperties = Json.parseToJsonElement("""[]"""),
        propertyDefaults =
          Json.parseToJsonElement(
              """
              {
                "version": 1,
                "resource": "work_item",
                "target": "transition",
                "values": {
                  "assignee": { "var": "user.currentUser" },
                  "property.resolution": "fixed",
                  "property.resolvedAt": { "var": "date.now" }
                }
              }
              """
                .trimIndent()
            )
            .jsonObject,
      ),
    isActive = true,
    createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
  )

private fun workItem(
  tenantId: UUID,
  projectId: UUID,
  config: IssueTypeConfigDetails,
): WorkItemRecord =
  WorkItemRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("iss"),
    tenantId = tenantId,
    projectId = projectId,
    issueTypeApiId = config.config.issueTypeApiId,
    issueTypeConfigApiId = config.config.apiId,
    key = "CORE-1",
    title = "Task",
    description = null,
    statusId = config.statuses.single().statusId,
    statusApiId = config.statuses.single().statusApiId,
    statusGroup = WorkItemStatusGroup.TODO,
    reporterId = UUID.randomUUID(),
    assigneeId = null,
    priorityApiId = null,
    reporterApiId = PublicId.new("usr"),
    assigneeApiId = null,
    sprintApiId = null,
    properties = JsonObject(emptyMap()),
    createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
  )

private fun workItemService(
  repository: WorkItemRepository,
  configs: IssueTypeConfigRepository,
  workflows: WorkflowConfigurationRepository,
  events: DomainEventPublisher,
  clock: Clock,
): WorkItemService {
  val fieldPermissions = mockk<WorkItemFieldPermissionService>()
  coEvery { fieldPermissions.canWriteField(any(), any()) } returns true
  coEvery { fieldPermissions.isFieldEditableInTransition(any(), any(), any()) } returns true
  coEvery { fieldPermissions.isFieldEditable(any(), any(), any()) } returns true
  coEvery { fieldPermissions.isFormFieldEditable(any(), any(), any()) } returns true
  val reconciler = WorkItemFieldMutationReconciler(fieldPermissions, clock)
  return WorkItemService(
    repository,
    configs,
    workflows,
    events,
    reconciler,
    fieldPermissions,
    mockk(relaxed = true),
    clock,
  )
}
