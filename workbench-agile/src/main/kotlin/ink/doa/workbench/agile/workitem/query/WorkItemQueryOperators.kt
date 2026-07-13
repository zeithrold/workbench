package ink.doa.workbench.agile.workitem.query

internal val REFERENCE_OPERATORS =
  setOf(
    QueryOperator.EQ,
    QueryOperator.NEQ,
    QueryOperator.IN,
    QueryOperator.NOT_IN,
    QueryOperator.IS_EMPTY,
    QueryOperator.IS_NOT_EMPTY,
  )

internal val TEXT_OPERATORS =
  REFERENCE_OPERATORS +
    setOf(
      QueryOperator.CONTAINS,
      QueryOperator.NOT_CONTAINS,
      QueryOperator.STARTS_WITH,
      QueryOperator.ENDS_WITH,
      QueryOperator.MATCHES,
    )

internal val LONG_TEXT_OPERATORS =
  setOf(
    QueryOperator.CONTAINS,
    QueryOperator.NOT_CONTAINS,
    QueryOperator.MATCHES,
    QueryOperator.IS_EMPTY,
    QueryOperator.IS_NOT_EMPTY,
  )

internal val NUMBER_OPERATORS =
  REFERENCE_OPERATORS +
    setOf(
      QueryOperator.LT,
      QueryOperator.LTE,
      QueryOperator.GT,
      QueryOperator.GTE,
      QueryOperator.BETWEEN,
    )

internal val BOOLEAN_OPERATORS =
  setOf(QueryOperator.EQ, QueryOperator.NEQ, QueryOperator.IS_EMPTY, QueryOperator.IS_NOT_EMPTY)

internal val DATE_OPERATORS =
  NUMBER_OPERATORS +
    setOf(
      QueryOperator.BEFORE,
      QueryOperator.ON_OR_BEFORE,
      QueryOperator.AFTER,
      QueryOperator.ON_OR_AFTER,
      QueryOperator.WITHIN,
    )

internal val ARRAY_OPERATORS =
  setOf(
    QueryOperator.CONTAINS,
    QueryOperator.NOT_CONTAINS,
    QueryOperator.HAS_ANY,
    QueryOperator.HAS_ALL,
    QueryOperator.HAS_NONE,
    QueryOperator.IS_EMPTY,
    QueryOperator.IS_NOT_EMPTY,
  )

internal val JSON_OPERATORS =
  setOf(
    QueryOperator.EQ,
    QueryOperator.NEQ,
    QueryOperator.CONTAINS,
    QueryOperator.NOT_CONTAINS,
    QueryOperator.IS_EMPTY,
    QueryOperator.IS_NOT_EMPTY,
  )
