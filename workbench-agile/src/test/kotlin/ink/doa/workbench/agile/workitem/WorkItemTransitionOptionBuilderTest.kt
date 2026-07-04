package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigStatusRecord
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
import ink.doa.workbench.core.workitem.template.TransitionFieldsLegacyMigrator
import ink.doa.workbench.core.workitem.template.WorkItemValueTemplateContext
import io.kotest.core.spec.style.StringSpec
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

class WorkItemTransitionOptionBuilderTest :
  StringSpec({
    val mutationSupport = mockk<WorkItemMutationSupport>()
    val fieldMutationReconciler = mockk<WorkItemFieldMutationReconciler>()
    val fieldPermissions = mockk<WorkItemFieldPermissionService>()
    val transitionValidator = WorkItemTransitionValidator(mockk(relaxed = true))
    val builder =
      WorkItemTransitionOptionBuilder(
        mutationSupport,
        fieldMutationReconciler,
        fieldPermissions,
        transitionValidator,
      )

    "build marks transition enabled when preconditions pass" {
      val buildContext = sampleBuildContext()
      val transition = sampleTransition(buildContext.config, buildContext.issue.statusId)
      coEvery { mutationSupport.templateContext(any()) } returns templateContext(buildContext)
      coEvery {
        fieldPermissions.isFormFieldEditable(any(), any(), any())
      } returns true
      coEvery { fieldMutationReconciler.buildFieldMeta(any(), any(), any(), any()) } returns
        emptyList()
      coEvery { fieldMutationReconciler.buildCommentMeta(any(), any()) } returns null

      val option = runBlocking { builder.build(transition, buildContext) }

      option.id shouldBe transition.apiId
      option.toStatusId shouldBe buildContext.config.statuses.single().statusApiId
    }

    "build disables transition when target status is unavailable" {
      val buildContext = sampleBuildContext()
      val missingStatusId = UUID.randomUUID()
      val transition =
        sampleTransition(buildContext.config, buildContext.issue.statusId).copy(
          toStatusId = missingStatusId,
          toStatusApiId = PublicId.new("sts"),
        )
      coEvery { mutationSupport.templateContext(any()) } returns templateContext(buildContext)
      coEvery {
        fieldPermissions.isFormFieldEditable(any(), any(), any())
      } returns false
      coEvery { fieldMutationReconciler.buildFieldMeta(any(), any(), any(), any()) } returns
        emptyList()
      coEvery { fieldMutationReconciler.buildCommentMeta(any(), any()) } returns null

      val option = runBlocking { builder.build(transition, buildContext) }

      option.enabled shouldBe false
      option.reason shouldBe "Transition target status is not available in this type config."
    }
  })

private fun templateContext(buildContext: TransitionOptionBuildContext) =
  WorkItemValueTemplateContext(
    tenantId = buildContext.tenantId,
    projectId = buildContext.projectId,
    currentUserApiId = "usr_test",
    currentProjectApiId = "prj_test",
    actorUserId = buildContext.actorUserId,
  )

private fun sampleBuildContext(): TransitionOptionBuildContext {
  val tenantId = UUID.randomUUID()
  val projectId = UUID.randomUUID()
  val actorUserId = UUID.randomUUID()
  val statusId = UUID.randomUUID()
  val issue =
    WorkItemRecord(
      id = UUID.randomUUID(),
      apiId = PublicId.new("iss"),
      tenantId = tenantId,
      projectId = projectId,
      issueTypeApiId = PublicId.new("typ"),
      issueTypeConfigApiId = PublicId.new("itc"),
      key = "CORE-1",
      title = "Issue",
      description = null,
      statusId = statusId,
      statusApiId = PublicId.new("sts"),
      statusGroup = WorkItemStatusGroup.TODO,
      reporterId = actorUserId,
      assigneeId = actorUserId,
      priorityApiId = null,
      reporterApiId = PublicId.new("usr"),
      assigneeApiId = PublicId.new("usr"),
      sprintApiId = null,
      properties = JsonObject(emptyMap()),
      createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
      updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
    )
  val config = sampleConfig(tenantId, statusId)
  return TransitionOptionBuildContext(
    issue = issue,
    config = config,
    tenantId = tenantId,
    projectId = projectId,
    actorUserId = actorUserId,
    currentProperties = emptyMap(),
    context = WorkItemConditionContext(issue, actorUserId, emptyMap()),
    permissionContext =
      WorkItemFieldPermissionContext(
        tenantId = tenantId,
        projectId = projectId,
        actorUserId = actorUserId,
        operation = FieldPermissionOperation.UPDATE,
      ),
  )
}

private fun sampleConfig(tenantId: UUID, statusId: UUID): IssueTypeConfigDetails {
  val configId = UUID.randomUUID()
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

private fun permissiveCondition(): JsonObject =
  JsonObject(
    mapOf(
      "field" to JsonPrimitive("statusGroup"),
      "op" to JsonPrimitive("eq"),
      "value" to JsonPrimitive("todo"),
    )
  )

private fun sampleTransition(
  config: IssueTypeConfigDetails,
  fromStatusId: UUID,
): WorkflowTransitionRecord {
  val status = config.statuses.single()
  return WorkflowTransitionRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("trn"),
    tenantId = config.config.tenantId,
    workflowId = config.config.workflowId,
    name = "Done",
    fromStatusId = fromStatusId,
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
