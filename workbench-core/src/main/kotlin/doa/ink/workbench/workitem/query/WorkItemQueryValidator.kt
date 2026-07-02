package doa.ink.workbench.workitem.query

import doa.ink.workbench.shared.errors.InvalidRequestException

class WorkItemQueryValidator {
  fun validate(query: WorkItemQuery) {
    if (query.version != CURRENT_VERSION)
      throw InvalidRequestException("Unsupported work item query version: ${query.version}")
    if (query.resource != RESOURCE)
      throw InvalidRequestException("Unsupported query resource: ${query.resource}")
    query.where?.let { validateCondition(it, depth = 0) }
  }

  private fun validateCondition(node: ConditionNode, depth: Int) {
    if (depth > MAX_DEPTH) throw InvalidRequestException("Work item query is too deeply nested.")
    when (node) {
      is ConditionNode.And -> validateLogical(node.args, depth)
      is ConditionNode.Or -> validateLogical(node.args, depth)
      is ConditionNode.Not -> validateCondition(node.arg, depth + 1)
      is ConditionNode.Predicate -> validatePredicate(node)
    }
  }

  private fun validateLogical(args: List<ConditionNode>, depth: Int) {
    if (args.isEmpty())
      throw InvalidRequestException("Logical query nodes must contain at least one child.")
    args.forEach { validateCondition(it, depth + 1) }
  }

  private fun validatePredicate(predicate: ConditionNode.Predicate) {
    if (predicate.field !in ALLOWED_FIELDS && !predicate.field.startsWith("property.")) {
      throw InvalidRequestException("Unknown work item query field: ${predicate.field}")
    }
    if (predicate.op !in ALLOWED_OPERATORS)
      throw InvalidRequestException("Unknown work item query operator: ${predicate.op}")
  }

  companion object {
    private const val CURRENT_VERSION = 1
    private const val RESOURCE = "work_item"
    private const val MAX_DEPTH = 8
    private val ALLOWED_FIELDS =
      setOf(
        "apiId",
        "key",
        "project",
        "issueType",
        "title",
        "status",
        "statusGroup",
        "assignee",
        "reporter",
        "createdAt",
        "updatedAt",
      )
    private val ALLOWED_OPERATORS =
      setOf(
        "eq",
        "neq",
        "in",
        "not_in",
        "contains",
        "is_empty",
        "is_not_empty",
        "lt",
        "lte",
        "gt",
        "gte",
        "within",
      )
  }
}
