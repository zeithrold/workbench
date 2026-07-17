package one.ztd.workbench.data.repository.workitem

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.agile.workitem.activity.WorkItemActivityFieldChange
import one.ztd.workbench.agile.workitem.model.UpdateWorkItemCommand
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyValue
import one.ztd.workbench.agile.workitem.model.WorkItemRecord
import one.ztd.workbench.agile.workitem.richtext.RichTextDocument
import one.ztd.workbench.data.persistence.postgres.workitem.PrioritiesTable
import one.ztd.workbench.data.persistence.postgres.workitem.SprintsTable
import one.ztd.workbench.data.persistence.postgres.workitem.loadUserEntityRef
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

internal fun buildWorkItemFieldChanges(
  before: WorkItemRecord,
  after: WorkItemRecord,
  command: UpdateWorkItemCommand,
  propertyValues: List<WorkItemPropertyValue>,
): List<WorkItemActivityFieldChange> = buildList {
  addAll(coreFieldChanges(before, after, command))
  addAll(propertyFieldChanges(before, propertyValues))
}

private fun coreFieldChanges(
  before: WorkItemRecord,
  after: WorkItemRecord,
  command: UpdateWorkItemCommand,
): List<WorkItemActivityFieldChange> {
  val changes = mutableListOf<WorkItemActivityFieldChange>()
  command.title
    ?.takeIf { it != before.title }
    ?.let { title ->
      changes +=
        WorkItemActivityFieldChange(
          path = "title",
          label = "Title",
          from = jsonString(before.title),
          to = jsonString(title),
        )
    }
  if (command.description != null && command.description != before.description) {
    changes +=
      WorkItemActivityFieldChange(
        path = "description",
        label = "Description",
        from = richTextJson(before.description),
        to = richTextJson(command.description),
      )
  }
  command.assigneeApiId?.let { assigneeApiId ->
    if (assigneeApiId != before.assigneeApiId?.value) {
      changes +=
        WorkItemActivityFieldChange(
          path = "assignee",
          label = "Assignee",
          from = entityJson(before.assigneeApiId?.value, before.assigneeId),
          to = entityJson(assigneeApiId, after.assigneeId),
        )
    }
  }
  command.priorityApiId?.let { priorityApiId ->
    if (priorityApiId != before.priorityApiId?.value) {
      changes +=
        WorkItemActivityFieldChange(
          path = "priority",
          label = "Priority",
          from = entityJson(before.priorityApiId?.value, null),
          to = entityJson(priorityApiId, null),
        )
    }
  }
  if (command.clearSprint || command.sprintApiId != null) {
    val nextSprint = after.sprintApiId?.value
    val previousSprint = before.sprintApiId?.value
    if (nextSprint != previousSprint) {
      changes +=
        WorkItemActivityFieldChange(
          path = "sprint",
          label = "Sprint",
          from = entityJson(previousSprint, null),
          to = entityJson(nextSprint, null),
        )
    }
  }
  return changes
}

private fun propertyFieldChanges(
  before: WorkItemRecord,
  propertyValues: List<WorkItemPropertyValue>,
): List<WorkItemActivityFieldChange> = propertyValues.mapNotNull { value ->
  val previous = before.properties[value.code]
  if (previous == value.value) {
    null
  } else {
    WorkItemActivityFieldChange(
      path = "properties.${value.code}",
      label = value.code,
      from = previous,
      to = value.value,
    )
  }
}

private fun entityJson(apiId: String?, internalId: UUID?): JsonElement {
  if (apiId == null) return JsonNull
  val display =
    when {
      internalId != null -> loadUserEntityRef(internalId)?.display
      apiId.startsWith("spr_") -> sprintDisplay(apiId)
      apiId.startsWith("pri_") -> priorityDisplay(apiId)
      else -> null
    }
  return JsonPrimitive(
    buildString {
      append(apiId)
      display?.let { append(" ($it)") }
    }
  )
}

private fun sprintDisplay(apiId: String): String? =
  SprintsTable.selectAll()
    .where { SprintsTable.apiId eq apiId }
    .singleOrNull()
    ?.get(SprintsTable.name)

private fun priorityDisplay(apiId: String): String? =
  PrioritiesTable.selectAll()
    .where { PrioritiesTable.apiId eq apiId }
    .singleOrNull()
    ?.get(PrioritiesTable.name)

private fun jsonString(value: String): JsonElement = JsonPrimitive(value)

private fun richTextJson(value: RichTextDocument?): JsonElement =
  value?.let { Json.encodeToJsonElement(RichTextDocument.serializer(), it) } ?: JsonNull
