package ink.doa.workbench.agile.workitem.query

import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.kernel.common.errors.requireValid
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

  private typealias OperatorValueValidator =
    (WorkItemQueryFieldType, QueryOperator, QueryValue) -> Unit

  private val operatorValueValidators: Map<QueryOperator, OperatorValueValidator> =
    mapOf(
      QueryOperator.IN to { _, op, value -> requireNonEmptyArray(op, value) },
      QueryOperator.NOT_IN to { _, op, value -> requireNonEmptyArray(op, value) },
      QueryOperator.HAS_ANY to { _, op, value -> requireNonEmptyArray(op, value) },
      QueryOperator.HAS_ALL to { _, op, value -> requireNonEmptyArray(op, value) },
      QueryOperator.HAS_NONE to { _, op, value -> requireNonEmptyArray(op, value) },
      QueryOperator.BETWEEN to { _, _, value -> requireBetween(value) },
      QueryOperator.WITHIN to { _, _, value -> requireRelativeDate(value) },
      QueryOperator.MATCHES to { _, op, value -> requireStringValue(op, value) },
      QueryOperator.STARTS_WITH to
        { fieldType, op, value ->
          validateTextLikeValue(fieldType, op, value)
        },
      QueryOperator.ENDS_WITH to
        { fieldType, op, value ->
          validateTextLikeValue(fieldType, op, value)
        },
      QueryOperator.CONTAINS to
        { fieldType, op, value ->
          validateTextLikeValue(fieldType, op, value)
        },
      QueryOperator.NOT_CONTAINS to
        { fieldType, op, value ->
          validateTextLikeValue(fieldType, op, value)
        },
    )

  fun validateValueShape(
    fieldType: WorkItemQueryFieldType,
    op: QueryOperator,
    value: QueryValue?,
  ) {
    if (op in unaryOperators) {
      requireValid(
        value == null,
        WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_VALUE_FORBIDDEN,
        "Operator ${op.wireName} does not accept a value.",
      )
      return
    }
    requireValid(
      value != null,
      WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_VALUE_REQUIRED,
      "Operator ${op.wireName} requires a value.",
    )
    val requiredValue = checkNotNull(value)
    operatorValueValidators[op]?.invoke(fieldType, op, requiredValue)
      ?: requireScalarOrVariable(op, requiredValue)
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
    requireValid(
      array.isNotEmpty(),
      WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_ARRAY_NOT_EMPTY_REQUIRED,
      "Operator ${op.wireName} requires a non-empty array value.",
    )
  }

  private fun requireBetween(value: QueryValue) {
    val range =
      value as? QueryValue.Between
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_BETWEEN_OBJECT_REQUIRED
        )
    requireValid(
      range.from != null || range.to != null,
      WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_BETWEEN_BOUND_REQUIRED,
    )
  }

  private fun requireRelativeDate(value: QueryValue) {
    val relative =
      value as? QueryValue.RelativeDate
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_RELATIVE_DATE_REQUIRED
        )
    requireValid(
      relative.amount > 0,
      WorkbenchErrorCode.WORK_ITEM_QUERY_RELATIVE_DATE_AMOUNT_POSITIVE,
    )
    requireValid(
      relative.anchor in dateVariableTypes.keys,
      WorkbenchErrorCode.WORK_ITEM_QUERY_RELATIVE_DATE_ANCHOR_UNKNOWN,
      "Unknown relative date anchor: ${relative.anchor}",
    )
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
    requireValid(
      primitive.isString,
      WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_STRING_REQUIRED,
      "Operator ${op.wireName} requires a string value.",
    )
  }

  private fun requireScalarOrVariable(op: QueryOperator, value: QueryValue) {
    when (value) {
      is QueryValue.Variable -> validateKnownVariable(value)
      is QueryValue.Literal -> validateScalarLiteral(op, value)
      is QueryValue.Between,
      is QueryValue.RelativeDate -> rejectStructuredValue(op)
    }
  }

  private fun validateKnownVariable(value: QueryValue.Variable) {
    requireValid(
      value.name in variableTypes,
      WorkbenchErrorCode.WORK_ITEM_QUERY_VARIABLE_UNKNOWN,
      "Unknown work item query variable: ${value.name}",
    )
  }

  private fun validateScalarLiteral(op: QueryOperator, value: QueryValue.Literal) {
    requireValid(
      value.value !is JsonArray && value.value !is JsonNull,
      WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_SINGLE_VALUE_REQUIRED,
      "Operator ${op.wireName} requires a single value.",
    )
  }

  private fun rejectStructuredValue(op: QueryOperator): Nothing {
    throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_SINGLE_VALUE_REQUIRED,
      "Operator ${op.wireName} requires a single value.",
    )
  }
}
