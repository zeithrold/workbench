package ink.doa.workbench.data.persistence.postgres.workitem.query

import ink.doa.workbench.agile.workitem.query.SortDirection

object PostgresWorkItemSearchAfter {
  fun afterCursor(specs: List<PostgresSortSpec>, cursorValues: List<Any?>): SqlFragment {
    require(specs.size == cursorValues.size) { "Cursor values must match sort stack size." }
    val clauses = mutableListOf<String>()
    val params = mutableListOf<Any?>()
    for (index in specs.indices) {
      val prefixEquals =
        (0 until index).map { prior ->
          val priorSpec = specs[prior]
          val clause = "${priorSpec.sql} IS NOT DISTINCT FROM ?"
          params += cursorValues[prior]
          clause
        }
      val current = specs[index]
      val operator = if (current.direction == SortDirection.ASC) ">" else "<"
      val compare = "${current.sql} $operator ?"
      params += cursorValues[index]
      val sql =
        if (prefixEquals.isEmpty()) {
          compare
        } else {
          "${prefixEquals.joinToString(" AND ")} AND $compare"
        }
      clauses += "($sql)"
    }
    return SqlFragment(clauses.joinToString(" OR "), params)
  }
}
