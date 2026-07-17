package one.ztd.workbench.agile.workitem.query

typealias WorkItemGroupKey = ConditionNode.Predicate

object WorkItemGroupKeyOps {
  val ALLOWED = setOf(QueryOperator.EQ, QueryOperator.IS_EMPTY)
}
