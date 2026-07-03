package doa.ink.workbench.core.workitem.query

import doa.ink.workbench.core.common.errors.InvalidRequestException

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
    WorkItemQueryValueValidators.validateValueShape(field.type, predicate.op, predicate.value)
  }

  private fun validateSort(sort: SortTerm) {
    val field = fieldResolver.resolve(sort.field)
    if (!field.sortable) {
      throw InvalidRequestException("Field ${sort.field.canonicalName} is not sortable.")
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
  }
}
