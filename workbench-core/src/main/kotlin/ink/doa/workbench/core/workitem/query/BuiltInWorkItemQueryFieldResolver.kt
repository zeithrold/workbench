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
    return WorkItemFieldDefinition(
      field = field,
      type = definition.type,
      sortable = definition.sortable,
      groupable = definition.groupable,
    )
  }

  private data class SystemFieldMeta(
    val type: WorkItemQueryFieldType,
    val sortable: Boolean,
    val groupable: Boolean = false,
  )

  private val SYSTEM_FIELDS =
    mapOf(
      "id" to SystemFieldMeta(WorkItemQueryFieldType.ID, sortable = false),
      "apiId" to SystemFieldMeta(WorkItemQueryFieldType.TEXT, sortable = false),
      "key" to SystemFieldMeta(WorkItemQueryFieldType.TEXT, sortable = true),
      "tenant" to SystemFieldMeta(WorkItemQueryFieldType.ID, sortable = false),
      "project" to SystemFieldMeta(WorkItemQueryFieldType.PROJECT, sortable = false),
      "issueType" to
        SystemFieldMeta(WorkItemQueryFieldType.SINGLE_SELECT, sortable = true, groupable = true),
      "title" to SystemFieldMeta(WorkItemQueryFieldType.TEXT, sortable = true),
      "description" to SystemFieldMeta(WorkItemQueryFieldType.LONG_TEXT, sortable = false),
      "status" to
        SystemFieldMeta(WorkItemQueryFieldType.SINGLE_SELECT, sortable = true, groupable = true),
      "statusGroup" to
        SystemFieldMeta(WorkItemQueryFieldType.SINGLE_SELECT, sortable = true, groupable = true),
      "priority" to
        SystemFieldMeta(WorkItemQueryFieldType.SINGLE_SELECT, sortable = true, groupable = true),
      "reporter" to SystemFieldMeta(WorkItemQueryFieldType.USER, sortable = true, groupable = true),
      "assignee" to SystemFieldMeta(WorkItemQueryFieldType.USER, sortable = true, groupable = true),
      "sprint" to SystemFieldMeta(WorkItemQueryFieldType.ID, sortable = true, groupable = true),
      "createdBy" to SystemFieldMeta(WorkItemQueryFieldType.USER, sortable = false),
      "updatedBy" to SystemFieldMeta(WorkItemQueryFieldType.USER, sortable = false),
      "createdAt" to SystemFieldMeta(WorkItemQueryFieldType.DATETIME, sortable = true),
      "updatedAt" to SystemFieldMeta(WorkItemQueryFieldType.DATETIME, sortable = true),
      "archivedAt" to SystemFieldMeta(WorkItemQueryFieldType.DATETIME, sortable = false),
      "deletedAt" to SystemFieldMeta(WorkItemQueryFieldType.DATETIME, sortable = false),
      "parent" to SystemFieldMeta(WorkItemQueryFieldType.ISSUE, sortable = false),
      "children.count" to SystemFieldMeta(WorkItemQueryFieldType.NUMBER, sortable = false),
      "children.issueType" to
        SystemFieldMeta(WorkItemQueryFieldType.SINGLE_SELECT, sortable = false),
    )
}
