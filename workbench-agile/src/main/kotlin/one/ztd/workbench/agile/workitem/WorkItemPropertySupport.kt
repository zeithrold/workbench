package one.ztd.workbench.agile.workitem

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.agile.workitem.model.CreateWorkItemCommand
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigDetails
import one.ztd.workbench.agile.workitem.model.UpdateWorkItemCommand
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyValue
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyValueValidator
import one.ztd.workbench.agile.workitem.richtext.RichTextProcessor
import one.ztd.workbench.agile.workitem.template.TemplateSystemFields
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode

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
    command.description?.let { inputs["description"] = it.content }
    command.assigneeApiId?.let { inputs["assignee"] = JsonPrimitive(it) }
    command.priorityApiId?.let { inputs["priority"] = JsonPrimitive(it) }
    command.sprintApiId?.let { inputs["sprint"] = JsonPrimitive(it) }
    inputs.putAll(command.properties)
    return inputs
  }

  fun applyDescriptionProcessing(command: UpdateWorkItemCommand): UpdateWorkItemCommand {
    if (command.description == null) return command
    val processed = RichTextProcessor.process(command.description)
    return command.copy(
      description = processed?.document,
      descriptionPlainText = processed?.plainText,
    )
  }
}
