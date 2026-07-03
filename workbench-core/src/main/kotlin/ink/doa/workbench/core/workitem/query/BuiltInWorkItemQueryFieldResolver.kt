package ink.doa.workbench.core.workitem.query

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode

object BuiltInWorkItemQueryFieldResolver : WorkItemQueryFieldResolver {
  override fun resolve(field: QueryField): WorkItemFieldDefinition {
    if (field is QueryField.Property) {
      return WorkItemFieldDefinition(field, WorkItemQueryFieldType.JSON, sortable = false)
    }
    val name = field.canonicalName
    val definition =
      SYSTEM_FIELDS[name]
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_FIELD_UNKNOWN,
          "Unknown work item query field: $name",
        )
    return WorkItemFieldDefinition(field, definition.first, definition.second)
  }

  private val SYSTEM_FIELDS =
    mapOf(
      "id" to (WorkItemQueryFieldType.ID to false),
      "apiId" to (WorkItemQueryFieldType.TEXT to false),
      "key" to (WorkItemQueryFieldType.TEXT to true),
      "tenant" to (WorkItemQueryFieldType.ID to false),
      "project" to (WorkItemQueryFieldType.PROJECT to false),
      "issueType" to (WorkItemQueryFieldType.SINGLE_SELECT to true),
      "title" to (WorkItemQueryFieldType.TEXT to true),
      "description" to (WorkItemQueryFieldType.LONG_TEXT to false),
      "status" to (WorkItemQueryFieldType.SINGLE_SELECT to true),
      "statusGroup" to (WorkItemQueryFieldType.SINGLE_SELECT to true),
      "priority" to (WorkItemQueryFieldType.SINGLE_SELECT to true),
      "reporter" to (WorkItemQueryFieldType.USER to true),
      "assignee" to (WorkItemQueryFieldType.USER to true),
      "sprint" to (WorkItemQueryFieldType.ID to true),
      "createdBy" to (WorkItemQueryFieldType.USER to false),
      "updatedBy" to (WorkItemQueryFieldType.USER to false),
      "createdAt" to (WorkItemQueryFieldType.DATETIME to true),
      "updatedAt" to (WorkItemQueryFieldType.DATETIME to true),
      "archivedAt" to (WorkItemQueryFieldType.DATETIME to false),
      "deletedAt" to (WorkItemQueryFieldType.DATETIME to false),
      "parent" to (WorkItemQueryFieldType.ISSUE to false),
      "children.count" to (WorkItemQueryFieldType.NUMBER to false),
    )
}
