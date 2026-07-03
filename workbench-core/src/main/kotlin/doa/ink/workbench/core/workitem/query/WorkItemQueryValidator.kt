@file:Suppress("TooManyFunctions", "ThrowsCount")

package doa.ink.workbench.core.workitem.query

import doa.ink.workbench.core.common.errors.InvalidRequestException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

class WorkItemQueryValidator(
  private val fieldResolver: WorkItemQueryFieldResolver = BuiltInWorkItemQueryFieldResolver
) {
  fun validate(query: WorkItemQuery) {
    validateEnvelope(query)
    val counter = PredicateCounter()
    query.where?.let { validateCondition(it, depth = 0, counter = counter) }
    query.sort.forEach(::validateSort)
  }

  fun validateEnvelope(query: WorkItemQuery) {
    if (query.version != WorkItemQuery.CURRENT_VERSION) {
      throw InvalidRequestException("Unsupported work item query version: ${query.version}")
    }
    if (query.resource != WorkItemQuery.RESOURCE) {
      throw InvalidRequestException("Unsupported query resource: ${query.resource}")
    }
  }

  private fun validateCondition(node: ConditionNode, depth: Int, counter: PredicateCounter) {
    if (depth > MAX_DEPTH) throw InvalidRequestException("Work item query is too deeply nested.")
    when (node) {
      is ConditionNode.And -> validateLogical(node.args, depth, counter)
      is ConditionNode.Or -> validateLogical(node.args, depth, counter)
      is ConditionNode.Not -> validateCondition(node.arg, depth + 1, counter)
      is ConditionNode.Predicate -> validatePredicate(node, counter)
    }
  }

  private fun validateLogical(args: List<ConditionNode>, depth: Int, counter: PredicateCounter) {
    if (args.isEmpty()) {
      throw InvalidRequestException("Logical query nodes must contain at least one child.")
    }
    args.forEach { validateCondition(it, depth + 1, counter) }
  }

  private fun validatePredicate(predicate: ConditionNode.Predicate, counter: PredicateCounter) {
    counter.increment()
    val field = fieldResolver.resolve(predicate.field)
    if (predicate.op !in field.type.supportedOperators) {
      throw InvalidRequestException(
        "Operator ${predicate.op.wireName} is not supported by field ${predicate.field.canonicalName}."
      )
    }
    validateValueShape(field.type, predicate.op, predicate.value)
  }

  private fun validateSort(sort: SortTerm) {
    val field = fieldResolver.resolve(sort.field)
    if (!field.sortable) {
      throw InvalidRequestException("Field ${sort.field.canonicalName} is not sortable.")
    }
  }

  private fun validateValueShape(
    fieldType: WorkItemQueryFieldType,
    op: QueryOperator,
    value: QueryValue?,
  ) {
    if (op in UNARY_OPERATORS) {
      if (value != null) {
        throw InvalidRequestException("Operator ${op.wireName} does not accept a value.")
      }
      return
    }
    if (value == null) {
      throw InvalidRequestException("Operator ${op.wireName} requires a value.")
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
        ?: throw InvalidRequestException("Operator ${op.wireName} requires an array value.")
    val array =
      literal.value as? JsonArray
        ?: throw InvalidRequestException("Operator ${op.wireName} requires an array value.")
    if (array.isEmpty()) {
      throw InvalidRequestException("Operator ${op.wireName} requires a non-empty array value.")
    }
  }

  private fun requireBetween(value: QueryValue) {
    val range =
      value as? QueryValue.Between
        ?: throw InvalidRequestException("Operator between requires an object value.")
    if (range.from == null && range.to == null) {
      throw InvalidRequestException("Operator between requires from or to.")
    }
  }

  private fun requireRelativeDate(value: QueryValue) {
    val relative =
      value as? QueryValue.RelativeDate
        ?: throw InvalidRequestException("Operator within requires a relativeDate value.")
    if (relative.amount <= 0) {
      throw InvalidRequestException("Relative date amount must be positive.")
    }
    if (relative.anchor !in DATE_VARIABLE_TYPES.keys) {
      throw InvalidRequestException("Unknown relative date anchor: ${relative.anchor}")
    }
  }

  private fun validateTextLikeValue(
    fieldType: WorkItemQueryFieldType,
    op: QueryOperator,
    value: QueryValue,
  ) {
    if (fieldType in ARRAY_FIELD_TYPES) {
      requireScalarOrVariable(op, value)
    } else {
      requireStringValue(op, value)
    }
  }

  private fun requireStringValue(op: QueryOperator, value: QueryValue) {
    val literal =
      value as? QueryValue.Literal
        ?: throw InvalidRequestException("Operator ${op.wireName} requires a string value.")
    val primitive =
      literal.value as? JsonPrimitive
        ?: throw InvalidRequestException("Operator ${op.wireName} requires a string value.")
    if (!primitive.isString) {
      throw InvalidRequestException("Operator ${op.wireName} requires a string value.")
    }
  }

  private fun requireScalarOrVariable(op: QueryOperator, value: QueryValue) {
    when (value) {
      is QueryValue.Variable -> {
        if (value.name !in VARIABLE_TYPES) {
          throw InvalidRequestException("Unknown work item query variable: ${value.name}")
        }
      }
      is QueryValue.Literal -> {
        if (value.value is JsonArray || value.value is JsonNull) {
          throw InvalidRequestException("Operator ${op.wireName} requires a single value.")
        }
      }
      is QueryValue.Between,
      is QueryValue.RelativeDate -> {
        throw InvalidRequestException("Operator ${op.wireName} requires a single value.")
      }
    }
  }

  private class PredicateCounter {
    private var count = 0

    fun increment() {
      count += 1
      if (count > MAX_PREDICATES) {
        throw InvalidRequestException("Work item query has too many predicates.")
      }
    }
  }

  companion object {
    private const val MAX_DEPTH = 8
    private const val MAX_PREDICATES = 64
    private val UNARY_OPERATORS = setOf(QueryOperator.IS_EMPTY, QueryOperator.IS_NOT_EMPTY)
    private val ARRAY_FIELD_TYPES =
      setOf(WorkItemQueryFieldType.MULTI_SELECT, WorkItemQueryFieldType.MULTI_USER)
    private val VARIABLE_TYPES =
      mapOf(
        "user.currentUser" to WorkItemQueryFieldType.USER,
        "project.currentProject" to WorkItemQueryFieldType.PROJECT,
        "date.now" to WorkItemQueryFieldType.DATETIME,
        "date.today" to WorkItemQueryFieldType.DATE,
        "date.startOfWeek" to WorkItemQueryFieldType.DATE,
        "date.endOfWeek" to WorkItemQueryFieldType.DATE,
      )
    private val DATE_VARIABLE_TYPES = VARIABLE_TYPES.filterValues {
      it == WorkItemQueryFieldType.DATE || it == WorkItemQueryFieldType.DATETIME
    }
  }
}

interface WorkItemQueryFieldResolver {
  fun resolve(field: QueryField): WorkItemFieldDefinition
}

data class WorkItemFieldDefinition(
  val field: QueryField,
  val type: WorkItemQueryFieldType,
  val sortable: Boolean,
)

enum class WorkItemQueryFieldType(val supportedOperators: Set<QueryOperator>) {
  ID(REFERENCE_OPERATORS),
  TEXT(TEXT_OPERATORS),
  LONG_TEXT(LONG_TEXT_OPERATORS),
  NUMBER(NUMBER_OPERATORS),
  BOOLEAN(BOOLEAN_OPERATORS),
  DATE(DATE_OPERATORS),
  DATETIME(DATE_OPERATORS),
  SINGLE_SELECT(REFERENCE_OPERATORS),
  MULTI_SELECT(ARRAY_OPERATORS),
  USER(REFERENCE_OPERATORS),
  MULTI_USER(ARRAY_OPERATORS),
  PROJECT(REFERENCE_OPERATORS),
  ISSUE(REFERENCE_OPERATORS),
  JSON(JSON_OPERATORS),
}

object BuiltInWorkItemQueryFieldResolver : WorkItemQueryFieldResolver {
  override fun resolve(field: QueryField): WorkItemFieldDefinition {
    if (field is QueryField.Property) {
      return WorkItemFieldDefinition(field, WorkItemQueryFieldType.JSON, sortable = false)
    }
    val name = field.canonicalName
    val definition =
      SYSTEM_FIELDS[name] ?: throw InvalidRequestException("Unknown work item query field: $name")
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

private val REFERENCE_OPERATORS =
  setOf(
    QueryOperator.EQ,
    QueryOperator.NEQ,
    QueryOperator.IN,
    QueryOperator.NOT_IN,
    QueryOperator.IS_EMPTY,
    QueryOperator.IS_NOT_EMPTY,
  )

private val TEXT_OPERATORS =
  REFERENCE_OPERATORS +
    setOf(
      QueryOperator.CONTAINS,
      QueryOperator.NOT_CONTAINS,
      QueryOperator.STARTS_WITH,
      QueryOperator.ENDS_WITH,
      QueryOperator.MATCHES,
    )

private val LONG_TEXT_OPERATORS =
  setOf(
    QueryOperator.CONTAINS,
    QueryOperator.NOT_CONTAINS,
    QueryOperator.MATCHES,
    QueryOperator.IS_EMPTY,
    QueryOperator.IS_NOT_EMPTY,
  )

private val NUMBER_OPERATORS =
  REFERENCE_OPERATORS +
    setOf(
      QueryOperator.LT,
      QueryOperator.LTE,
      QueryOperator.GT,
      QueryOperator.GTE,
      QueryOperator.BETWEEN,
    )

private val BOOLEAN_OPERATORS =
  setOf(QueryOperator.EQ, QueryOperator.NEQ, QueryOperator.IS_EMPTY, QueryOperator.IS_NOT_EMPTY)

private val DATE_OPERATORS =
  NUMBER_OPERATORS +
    setOf(
      QueryOperator.BEFORE,
      QueryOperator.ON_OR_BEFORE,
      QueryOperator.AFTER,
      QueryOperator.ON_OR_AFTER,
      QueryOperator.WITHIN,
    )

private val ARRAY_OPERATORS =
  setOf(
    QueryOperator.CONTAINS,
    QueryOperator.NOT_CONTAINS,
    QueryOperator.HAS_ANY,
    QueryOperator.HAS_ALL,
    QueryOperator.HAS_NONE,
    QueryOperator.IS_EMPTY,
    QueryOperator.IS_NOT_EMPTY,
  )

private val JSON_OPERATORS =
  setOf(
    QueryOperator.EQ,
    QueryOperator.NEQ,
    QueryOperator.CONTAINS,
    QueryOperator.NOT_CONTAINS,
    QueryOperator.IS_EMPTY,
    QueryOperator.IS_NOT_EMPTY,
  )
