package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.agile.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.agile.workitem.model.TransitionPersistenceCommand
import ink.doa.workbench.agile.workitem.model.TransitionRequest
import ink.doa.workbench.agile.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.agile.workitem.richtext.RichTextProcessor
import ink.doa.workbench.agile.workitem.template.TransitionFieldsParser
import ink.doa.workbench.agile.workitem.template.WorkItemTransitionFieldsTemplate
import ink.doa.workbench.agile.workitem.template.WorkItemValueTemplateContext
import ink.doa.workbench.agile.workitem.template.WorkItemValueTemplateTarget
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.stereotype.Component

data class TransitionMutationPlan(
  val persistence: TransitionPersistenceCommand,
  val propertyValues: List<WorkItemPropertyValue>,
  val commentBody: ink.doa.workbench.agile.workitem.richtext.RichTextDocument?,
)

data class CreateMutationPlan(
  val command: CreateWorkItemCommand,
  val propertyValues: List<WorkItemPropertyValue>,
)

@Component
class WorkItemFieldMutationPipeline(
  val engine: WorkItemFieldMutationEngine,
  private val transitionFieldsParser: TransitionFieldsParser,
) {
  suspend fun applyCreate(
    command: CreateWorkItemCommand,
    config: IssueTypeConfigDetails,
    templateContext: WorkItemValueTemplateContext,
    permissionContext: WorkItemFieldPermissionContext,
    createFields: JsonElement,
  ): CreateMutationPlan {
    val template = transitionFieldsParser.parseCreateFields(createFields)
    val reconciled =
      engine.applyTemplate(
        FieldReconciliationContext(
          template = template,
          expectedTarget = WorkItemValueTemplateTarget.CREATE,
          config = config,
          templateContext = templateContext,
          currentProperties = emptyMap(),
          userProperties = createFieldInputs(command),
          permissionContext = permissionContext,
        )
      )
    val effectiveCommand = applyCreateSystemFields(command, reconciled.systemFields)
    val propertyValues = normalizeProperties(config, reconciled.propertyValues)
    return CreateMutationPlan(effectiveCommand, propertyValues)
  }

  suspend fun applyTransition(
    request: TransitionRequest,
    context: WorkItemTransitionContext,
    fieldsTemplate: WorkItemTransitionFieldsTemplate,
  ): TransitionMutationPlan {
    val reconciled =
      engine.applyTemplate(
        FieldReconciliationContext(
          template = fieldsTemplate,
          expectedTarget = WorkItemValueTemplateTarget.TRANSITION,
          config = context.config,
          templateContext = context.templateContext,
          currentProperties = context.currentProperties,
          userProperties = transitionFieldInputs(request),
          permissionContext = context.permissionContext,
        )
      )
    val persistence = applyTransitionSystemFields(request, reconciled.systemFields)
    val commentBody =
      engine.reconcileTransitionComment(
        spec = fieldsTemplate.comment,
        templateContext = context.templateContext,
        userComment = request.comment,
      )
    val propertyValues = normalizeProperties(context.config, reconciled.propertyValues)
    return TransitionMutationPlan(
      persistence = persistence,
      propertyValues = propertyValues,
      commentBody = commentBody,
    )
  }

  suspend fun planCreateForm(
    config: IssueTypeConfigDetails,
    createFields: kotlinx.serialization.json.JsonElement,
    templateContext: WorkItemValueTemplateContext,
    permissionContext: WorkItemFieldPermissionContext,
  ): FieldFormPlan {
    val fieldsTemplate = transitionFieldsParser.parseCreateFields(createFields)
    return engine.planForm(
      template = fieldsTemplate,
      config = config,
      templateContext = templateContext,
      permissionContext = permissionContext,
    )
  }

  private fun createFieldInputs(command: CreateWorkItemCommand): Map<String, JsonElement> {
    val inputs = mutableMapOf<String, JsonElement>("title" to JsonPrimitive(command.title))
    command.description?.let { inputs["description"] = it.content }
    command.assigneeApiId?.let { inputs["assignee"] = JsonPrimitive(it) }
    command.priorityApiId?.let { inputs["priority"] = JsonPrimitive(it) }
    command.sprintApiId?.let { inputs["sprint"] = JsonPrimitive(it) }
    inputs.putAll(command.properties)
    return inputs
  }

  private fun transitionFieldInputs(request: TransitionRequest): Map<String, JsonElement> {
    val inputs = linkedMapOf<String, JsonElement>()
    request.title?.let { inputs["title"] = JsonPrimitive(it) }
    request.description?.let { inputs["description"] = it.content }
    inputs.putAll(request.properties)
    return inputs
  }

  private fun applyCreateSystemFields(
    command: CreateWorkItemCommand,
    systemFields: Map<String, String?>,
  ): CreateWorkItemCommand {
    val description =
      command.description ?: RichTextProcessor.fromPlainText(systemFields["description"])
    val processed = RichTextProcessor.process(description)
    return command.copy(
      title = systemFields["title"] ?: command.title,
      description = processed?.document,
      descriptionPlainText = processed?.plainText,
      assigneeApiId = systemFields["assignee"] ?: command.assigneeApiId,
      priorityApiId = systemFields["priority"] ?: command.priorityApiId,
      sprintApiId = systemFields["sprint"] ?: command.sprintApiId,
    )
  }

  private fun applyTransitionSystemFields(
    request: TransitionRequest,
    systemFields: Map<String, String?>,
  ): TransitionPersistenceCommand {
    val description =
      request.description ?: RichTextProcessor.fromPlainText(systemFields["description"])
    val processed = RichTextProcessor.process(description)
    return TransitionPersistenceCommand(
      tenantId = request.tenantId,
      projectId = request.projectId,
      workItemApiId = request.workItemApiId,
      actorUserId = request.actorUserId,
      title = request.title ?: systemFields["title"],
      description = processed?.document,
      descriptionPlainText = processed?.plainText,
      assigneeApiId = systemFields["assignee"],
      priorityApiId = systemFields["priority"],
      sprintApiId = systemFields["sprint"],
    )
  }

  private fun normalizeProperties(
    config: IssueTypeConfigDetails,
    input: Map<String, JsonElement>,
  ): List<WorkItemPropertyValue> = WorkItemPropertySupport.normalizeProperties(config, input)
}
