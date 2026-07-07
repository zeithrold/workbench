package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValueValidator
import ink.doa.workbench.core.workitem.richtext.RichTextProcessor
import ink.doa.workbench.core.workitem.template.TemplateSystemFields
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

internal object WorkItemPropertySupport {
  fun normalizeProperties(
    config: IssueTypeConfigDetails,
    input: Map<String, JsonElement>,
  ): List<WorkItemPropertyValue> {
    if (input.isEmpty()) return emptyList()
    val byKey =
      config.properties
        .flatMap { property ->
          listOf(property.propertyApiId.value to property, property.code to property)
        }
        .toMap()
    return input.map { (key, value) ->
      val property =
        byKey[key]
          ?: throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_PROPERTY_UNAVAILABLE,
            "Property is not available in this config: $key",
          )
      WorkItemPropertyValueValidator.validate(property, value)
      WorkItemPropertyValue(
        propertyId = property.propertyId,
        propertyApiId = property.propertyApiId,
        code = property.code,
        dataType = property.dataType,
        value = value,
      )
    }
  }

  fun Map<String, JsonElement>.filterPropertyInputs(): Map<String, JsonElement> = filterKeys {
    it !in TemplateSystemFields.WRITABLE
  }

  fun createFieldInputs(command: CreateWorkItemCommand): Map<String, JsonElement> {
    val inputs = mutableMapOf<String, JsonElement>("title" to JsonPrimitive(command.title))
    command.description?.let { inputs["description"] = JsonPrimitive(it) }
    command.assigneeApiId?.let { inputs["assignee"] = JsonPrimitive(it) }
    command.priorityApiId?.let { inputs["priority"] = JsonPrimitive(it) }
    command.sprintApiId?.let { inputs["sprint"] = JsonPrimitive(it) }
    inputs.putAll(command.properties)
    return inputs
  }

  fun applyDescriptionProcessing(command: UpdateWorkItemCommand): UpdateWorkItemCommand {
    if (command.description == null) return command
    val processed = RichTextProcessor.processDescriptionInput(command.description)
    return command.copy(
      description = processed?.html,
      descriptionPlainText = processed?.plainText,
    )
  }
}
