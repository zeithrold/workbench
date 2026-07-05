package ink.doa.workbench.core.workitem.query

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode

class WorkItemGroupKeyValidator(
  private val fieldResolver: WorkItemQueryFieldResolver = BuiltInWorkItemQueryFieldResolver
) {
  fun validateGroupKey(key: WorkItemGroupKey, expectedField: QueryField) {
    if (key.field != expectedField) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_GROUP_KEY_FIELD_MISMATCH,
        "Group key field ${key.field.canonicalName} does not match ${expectedField.canonicalName}.",
      )
    }
    if (key.op !in WorkItemGroupKeyOps.ALLOWED) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_GROUP_KEY_OPERATOR_UNSUPPORTED,
        "Group key operator ${key.op.wireName} is not supported.",
      )
    }
    val field = fieldResolver.resolve(key.field)
    if (!field.groupable) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_FIELD_NOT_GROUPABLE,
        "Field ${key.field.canonicalName} is not groupable.",
      )
    }
    when (key.op) {
      QueryOperator.EQ ->
        WorkItemQueryValueValidators.validateValueShape(field.type, key.op, key.value)
      QueryOperator.IS_EMPTY -> {
        if (key.value != null) {
          throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_VALUE_FORBIDDEN,
            "Group key is_empty must not include a value.",
          )
        }
      }
      else -> error("Unreachable")
    }
  }

  fun validateGroupScope(scope: WorkItemSearchGroupScope, groupField: QueryField?) {
    if (scope.includeGroupKeys.isEmpty() && scope.excludeGroupKeys.isEmpty()) return
    val expected =
      groupField
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_GROUP_REQUIRED,
          "Work item query group is required when search scope includes group keys.",
        )
    scope.includeGroupKeys.forEach { validateGroupKey(it, expected) }
    scope.excludeGroupKeys.forEach { validateGroupKey(it, expected) }
  }
}
