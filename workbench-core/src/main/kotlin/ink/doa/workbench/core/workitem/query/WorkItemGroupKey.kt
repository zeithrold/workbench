package ink.doa.workbench.core.workitem.query

typealias WorkItemGroupKey = ConditionNode.Predicate

object WorkItemGroupKeyOps {
  val ALLOWED = setOf(QueryOperator.EQ, QueryOperator.IS_EMPTY)
}
