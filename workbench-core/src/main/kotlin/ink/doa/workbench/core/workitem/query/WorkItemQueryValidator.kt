package ink.doa.workbench.core.workitem.query

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode

class WorkItemQueryValidator(
  private val fieldResolver: WorkItemQueryFieldResolver = BuiltInWorkItemQueryFieldResolver
) {
  fun validate(query: WorkItemQuery) {
    validateEnvelope(query)
    val counter = PredicateCounter()
    query.where?.let { validateCondition(it, depth = 0, counter = counter) }
    query.sort.forEach(::validateSort)
    query.group?.let(::validateGroup)
  }

  fun validate(query: WorkItemQuery, groupScope: WorkItemSearchGroupScope) {
    validate(query)
    WorkItemGroupKeyValidator(fieldResolver).validateGroupScope(groupScope, query.group?.field)
  }

  fun validateEnvelope(query: WorkItemQuery) {
    if (query.version != WorkItemQuery.CURRENT_VERSION) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_VERSION_UNSUPPORTED,
        "Unsupported work item query version: ${query.version}",
      )
    }
    if (query.resource != WorkItemQuery.RESOURCE) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_RESOURCE_UNSUPPORTED,
        "Unsupported query resource: ${query.resource}",
      )
    }
  }

  private fun validateCondition(node: ConditionNode, depth: Int, counter: PredicateCounter) {
    if (depth > MAX_DEPTH) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_QUERY_TOO_DEEPLY_NESTED)
    }
    when (node) {
      is ConditionNode.And -> validateLogical(node.args, depth, counter)
      is ConditionNode.Or -> validateLogical(node.args, depth, counter)
      is ConditionNode.Not -> validateCondition(node.arg, depth + 1, counter)
      is ConditionNode.Predicate -> validatePredicate(node, counter)
    }
  }

  private fun validateLogical(args: List<ConditionNode>, depth: Int, counter: PredicateCounter) {
    if (args.isEmpty()) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_QUERY_LOGICAL_EMPTY)
    }
    args.forEach { validateCondition(it, depth + 1, counter) }
  }

  private fun validatePredicate(predicate: ConditionNode.Predicate, counter: PredicateCounter) {
    counter.increment()
    val field = fieldResolver.resolve(predicate.field)
    if (predicate.op !in field.type.supportedOperators) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_UNSUPPORTED_FOR_FIELD,
        "Operator ${predicate.op.wireName} is not supported by field ${predicate.field.canonicalName}.",
      )
    }
    WorkItemQueryValueValidators.validateValueShape(field.type, predicate.op, predicate.value)
  }

  private fun validateSort(sort: SortTerm) {
    val field = fieldResolver.resolve(sort.field)
    if (!field.sortable) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_FIELD_NOT_SORTABLE,
        "Field ${sort.field.canonicalName} is not sortable.",
      )
    }
  }

  private fun validateGroup(group: WorkItemGroupTerm) {
    val field = fieldResolver.resolve(group.field)
    if (!field.groupable) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_FIELD_NOT_GROUPABLE,
        "Field ${group.field.canonicalName} is not groupable.",
      )
    }
  }

  private class PredicateCounter {
    private var count = 0

    fun increment() {
      count += 1
      if (count > MAX_PREDICATES) {
        throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_QUERY_TOO_MANY_PREDICATES)
      }
    }
  }

  companion object {
    private const val MAX_DEPTH = 8
    private const val MAX_PREDICATES = 64
  }
}
