@file:Suppress("ThrowsCount")

package doa.ink.workbench.core.workitem.query

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.common.errors.WorkbenchErrorCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

internal object WorkItemQueryValueValidators {
  val unaryOperators = setOf(QueryOperator.IS_EMPTY, QueryOperator.IS_NOT_EMPTY)

  val arrayFieldTypes =
    setOf(WorkItemQueryFieldType.MULTI_SELECT, WorkItemQueryFieldType.MULTI_USER)

  val variableTypes =
    mapOf(
      "user.currentUser" to WorkItemQueryFieldType.USER,
      "project.currentProject" to WorkItemQueryFieldType.PROJECT,
      "date.now" to WorkItemQueryFieldType.DATETIME,
      "date.today" to WorkItemQueryFieldType.DATE,
      "date.startOfWeek" to WorkItemQueryFieldType.DATE,
      "date.endOfWeek" to WorkItemQueryFieldType.DATE,
    )

  val dateVariableTypes = variableTypes.filterValues {
    it == WorkItemQueryFieldType.DATE || it == WorkItemQueryFieldType.DATETIME
  }

  fun validateValueShape(
    fieldType: WorkItemQueryFieldType,
    op: QueryOperator,
    value: QueryValue?,
  ) {
    if (op in unaryOperators) {
      if (value != null) {
        throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_VALUE_FORBIDDEN,
          "Operator ${op.wireName} does not accept a value.",
        )
      }
      return
    }
    if (value == null) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_VALUE_REQUIRED,
        "Operator ${op.wireName} requires a value.",
      )
    }
    when (op) {
      QueryOperator.IN,
      QueryOperator.NOT_IN,
      QueryOperator.HAS_ANY,
      QueryOperator.HAS_ALL,
      QueryOperator.HAS_NONE -> requireNonEmptyArray(op, value)
      QueryOperator.BETWEEN -> requireBetween(value)
      QueryOperator.WITHIN -> requireRelativeDate(value)
      QueryOperator.MATCHES -> requireStringValue(op, value)
      QueryOperator.STARTS_WITH,
      QueryOperator.ENDS_WITH,
      QueryOperator.CONTAINS,
      QueryOperator.NOT_CONTAINS -> validateTextLikeValue(fieldType, op, value)
      else -> requireScalarOrVariable(op, value)
    }
  }

  private fun requireNonEmptyArray(op: QueryOperator, value: QueryValue) {
    val literal =
      value as? QueryValue.Literal
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_ARRAY_REQUIRED,
          "Operator ${op.wireName} requires an array value.",
        )
    val array =
      literal.value as? JsonArray
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_ARRAY_REQUIRED,
          "Operator ${op.wireName} requires an array value.",
        )
    if (array.isEmpty()) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_ARRAY_NOT_EMPTY_REQUIRED,
        "Operator ${op.wireName} requires a non-empty array value.",
      )
    }
  }

  private fun requireBetween(value: QueryValue) {
    val range =
      value as? QueryValue.Between
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_BETWEEN_OBJECT_REQUIRED
        )
    if (range.from == null && range.to == null) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_BETWEEN_BOUND_REQUIRED
      )
    }
  }

  private fun requireRelativeDate(value: QueryValue) {
    val relative =
      value as? QueryValue.RelativeDate
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_RELATIVE_DATE_REQUIRED
        )
    if (relative.amount <= 0) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_RELATIVE_DATE_AMOUNT_POSITIVE
      )
    }
    if (relative.anchor !in dateVariableTypes.keys) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_RELATIVE_DATE_ANCHOR_UNKNOWN,
        "Unknown relative date anchor: ${relative.anchor}",
      )
    }
  }

  private fun validateTextLikeValue(
    fieldType: WorkItemQueryFieldType,
    op: QueryOperator,
    value: QueryValue,
  ) {
    if (fieldType in arrayFieldTypes) {
      requireScalarOrVariable(op, value)
    } else {
      requireStringValue(op, value)
    }
  }

  private fun requireStringValue(op: QueryOperator, value: QueryValue) {
    val literal =
      value as? QueryValue.Literal
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_STRING_REQUIRED,
          "Operator ${op.wireName} requires a string value.",
        )
    val primitive =
      literal.value as? JsonPrimitive
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_STRING_REQUIRED,
          "Operator ${op.wireName} requires a string value.",
        )
    if (!primitive.isString) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_STRING_REQUIRED,
        "Operator ${op.wireName} requires a string value.",
      )
    }
  }

  private fun requireScalarOrVariable(op: QueryOperator, value: QueryValue) {
    when (value) {
      is QueryValue.Variable -> {
        if (value.name !in variableTypes) {
          throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_QUERY_VARIABLE_UNKNOWN,
            "Unknown work item query variable: ${value.name}",
          )
        }
      }
      is QueryValue.Literal -> {
        if (value.value is JsonArray || value.value is JsonNull) {
          throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_SINGLE_VALUE_REQUIRED,
            "Operator ${op.wireName} requires a single value.",
          )
        }
      }
      is QueryValue.Between,
      is QueryValue.RelativeDate -> {
        throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_SINGLE_VALUE_REQUIRED,
          "Operator ${op.wireName} requires a single value.",
        )
      }
    }
  }
}
