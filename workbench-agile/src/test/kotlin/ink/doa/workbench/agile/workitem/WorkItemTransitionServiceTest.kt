package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.WorkflowConfigurationRepository
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigStatusRecord
import ink.doa.workbench.core.workitem.model.TransitionWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class WorkItemTransitionServiceTest :
  StringSpec({
    val repository = mockk<WorkItemRepository>()
    val workflows = mockk<WorkflowConfigurationRepository>()
    val mutationSupport = mockk<WorkItemMutationSupport>()
    val fieldMutationReconciler = mockk<WorkItemFieldMutationReconciler>()
    val commentService = mockk<WorkItemCommentService>()
    val fieldPermissions = mockk<WorkItemFieldPermissionService>()
    val transitionValidator = WorkItemTransitionValidator(repository)
    val transitionOptions =
      WorkItemTransitionOptionBuilder(
        mutationSupport,
        fieldMutationReconciler,
        fieldPermissions,
        transitionValidator,
      )
    val collaborators =
      WorkItemTransitionCollaborators(
        mutationSupport,
        fieldMutationReconciler,
        commentService,
        transitionValidator,
        transitionOptions,
      )
    val service = WorkItemTransitionService(repository, workflows, collaborators)
    val json = Json { ignoreUnknownKeys = true }

    "availableTransitions returns options for current status" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val config = sampleConfig(tenantId)
      val issue = sampleIssue(tenantId, projectId, config, actorId)
      val transition = sampleTransition(config)

      coEvery { repository.findByApiId(tenantId, projectId, issue.apiId.value) } returns issue
      coEvery { mutationSupport.requireConfig(tenantId, issue.issueTypeConfigApiId.value) } returns
        config
      coEvery { repository.listPropertyValues(tenantId, issue.id) } returns emptyMap()
      coEvery {
        repository.countChildrenNotInStatusGroups(tenantId, issue.id, setOf("done"))
      } returns 0
      coEvery { workflows.listTransitions(any(), any()) } returns listOf(transition)
      coEvery {
        fieldPermissions.isFormFieldEditable(any(), any(), any())
      } returns true
      coEvery { mutationSupport.templateContext(any()) } returns mockk(relaxed = true)
      coEvery { fieldMutationReconciler.buildFieldMeta(any(), any(), any(), any()) } returns
        emptyList()
      coEvery { fieldMutationReconciler.buildCommentMeta(any(), any()) } returns null

      val options = runBlocking {
        service.availableTransitions(tenantId, projectId, issue.apiId.value, actorId)
      }

      options shouldHaveSize 1
      options.single().id shouldBe transition.apiId
    }

    "transition rejects unknown work item" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()

      coEvery { repository.findByApiId(tenantId, projectId, "iss_missing") } returns null

      shouldThrow<ResourceNotFoundException> {
          runBlocking {
            service.transition(
              TransitionWorkItemCommand(
                tenantId = tenantId,
                projectId = projectId,
                workItemApiId = "iss_missing",
                transitionApiId = "trn_done",
                actorUserId = UUID.randomUUID(),
              )
            )
          }
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND
    }
  })

private fun permissiveCondition(): JsonObject =
  JsonObject(
    mapOf(
      "field" to JsonPrimitive("statusGroup"),
      "op" to JsonPrimitive("eq"),
      "value" to JsonPrimitive("todo"),
    )
  )

private fun sampleIssue(
  tenantId: UUID,
  projectId: UUID,
  config: IssueTypeConfigDetails,
  actorId: UUID,
): WorkItemRecord {
  val status = config.statuses.single()
  return WorkItemRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("iss"),
    tenantId = tenantId,
    projectId = projectId,
    issueTypeApiId = config.config.issueTypeApiId,
    issueTypeConfigApiId = config.config.apiId,
    key = "CORE-1",
    title = "Issue",
    description = null,
    statusId = status.statusId,
    statusApiId = status.statusApiId,
    statusGroup = WorkItemStatusGroup.TODO,
    reporterId = actorId,
    assigneeId = actorId,
    priorityApiId = null,
    reporterApiId = PublicId.new("usr"),
    assigneeApiId = PublicId.new("usr"),
    sprintApiId = null,
    properties = JsonObject(emptyMap()),
    createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
  )
}

private fun sampleConfig(tenantId: UUID): IssueTypeConfigDetails {
  val configId = UUID.randomUUID()
  val statusId = UUID.randomUUID()
  return IssueTypeConfigDetails(
    config =
      IssueTypeConfigRecord(
        id = configId,
        apiId = PublicId.new("itc"),
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
        validFrom = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        validTo = null,
        createdBy = null,
        createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        createFields = JsonObject(emptyMap()),
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

private fun sampleTransition(config: IssueTypeConfigDetails): WorkflowTransitionRecord {
  val status = config.statuses.single()
  return WorkflowTransitionRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("trn"),
    tenantId = config.config.tenantId,
    workflowId = config.config.workflowId,
    name = "Done",
    fromStatusId = status.statusId,
    fromStatusApiId = status.statusApiId,
    toStatusId = status.statusId,
    toStatusApiId = status.statusApiId,
    rank = 100,
    permissionCondition = permissiveCondition(),
    preconditionAst = permissiveCondition(),
    fields =
      Json.parseToJsonElement(
          """
          {
            "version": 1,
            "resource": "work_item",
            "target": "transition",
            "fields": {
              "title": { "participation": "optional" }
            }
          }
          """
            .trimIndent()
        )
        .jsonObject,
    isActive = true,
    createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
  )
}
