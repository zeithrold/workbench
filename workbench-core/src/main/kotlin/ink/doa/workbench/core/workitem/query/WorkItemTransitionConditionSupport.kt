package ink.doa.workbench.core.workitem.query

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import kotlinx.serialization.json.JsonObject

val WORK_ITEM_TRANSITION_CONDITION_OPERATORS: Set<QueryOperator> =
  setOf(
    QueryOperator.EQ,
    QueryOperator.NEQ,
    QueryOperator.IN,
    QueryOperator.NOT_IN,
    QueryOperator.IS_EMPTY,
    QueryOperator.IS_NOT_EMPTY,
    QueryOperator.GT,
    QueryOperator.GTE,
    QueryOperator.LT,
    QueryOperator.LTE,
    QueryOperator.CONTAINS,
    QueryOperator.NOT_CONTAINS,
    QueryOperator.HAS_ANY,
    QueryOperator.HAS_ALL,
    QueryOperator.HAS_NONE,
  )

val WORK_ITEM_TRANSITION_CONDITION_VARIABLES: Set<String> =
  setOf(
    "user.currentUser",
    "issue.reporter",
    "issue.assignee",
  )

class WorkItemTransitionConditionValidator {
  fun validate(ast: JsonObject) {
    WorkItemConditionSyntax.validate(ast)
    val node = WorkItemConditionJson.parse(ast) ?: return
    validateNode(node)
  }

  private fun validateNode(node: ConditionNode) {
    when (node) {
      is ConditionNode.And -> node.args.forEach(::validateNode)
      is ConditionNode.Or -> node.args.forEach(::validateNode)
      is ConditionNode.Not -> validateNode(node.arg)
      is ConditionNode.Predicate -> validatePredicate(node)
    }
  }

  private fun validatePredicate(predicate: ConditionNode.Predicate) {
    if (predicate.op !in WORK_ITEM_TRANSITION_CONDITION_OPERATORS) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
        "Unsupported condition operator: ${predicate.op.wireName}",
      )
    }
    predicate.value?.let { validateValue(it) }
  }

  private fun validateValue(value: QueryValue) {
    when (value) {
      is QueryValue.Literal -> Unit
      is QueryValue.Variable ->
        if (value.name !in WORK_ITEM_TRANSITION_CONDITION_VARIABLES) {
          throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
            "Unsupported condition variable: ${value.name}",
          )
        }
      else ->
        throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_CONDITION_UNSUPPORTED,
          "Unsupported condition value.",
        )
    }
  }
}
