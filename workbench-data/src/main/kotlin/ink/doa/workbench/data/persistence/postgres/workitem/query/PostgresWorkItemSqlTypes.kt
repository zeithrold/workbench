package ink.doa.workbench.data.persistence.postgres.workitem.query

import ink.doa.workbench.agile.workitem.query.QueryOperator
import ink.doa.workbench.agile.workitem.query.QueryValue
import ink.doa.workbench.agile.workitem.query.WorkItemFieldDefinition

data class SqlFragment(val sql: String, val params: List<Any?> = emptyList()) {
  fun parenthesized(): SqlFragment = copy(sql = "($sql)")
}

data class PostgresWorkItemQueryPlan(
  val fromSql: String,
  val where: SqlFragment,
  val orderBySql: String,
  val params: List<Any?>,
)

data class PostgresWorkItemSearchPlan(
  val fromSql: String,
  val where: SqlFragment,
  val orderBySql: String,
  val sortStack: List<PostgresSortSpec>,
  val params: List<Any?>,
  val sortParams: List<Any?>,
)

data class PostgresWorkItemGroupsPlan(
  val fromSql: String,
  val where: SqlFragment,
  val groupSql: String,
  val groupParams: List<Any?>,
  val orderDirection: String,
  val nullsClause: String,
  val fetchLimit: Int,
  val params: List<Any?>,
  val cursorPredicate: SqlFragment?,
)

sealed interface PostgresWorkItemField {
  val definition: WorkItemFieldDefinition
  val valueSql: String
  val sortSql: String
  val groupSql: String

  data class System(
    override val definition: WorkItemFieldDefinition,
    override val valueSql: String,
    override val sortSql: String = valueSql,
    override val groupSql: String = sortSql,
    val predicateCompiler: ((QueryOperator, QueryValue?) -> SqlFragment)? = null,
  ) : PostgresWorkItemField

  data class Property(
    override val definition: WorkItemFieldDefinition,
    override val valueSql: String,
    val identitySql: String,
    val identityParams: List<Any?>,
    override val groupSql: String,
  ) : PostgresWorkItemField {
    override val sortSql: String = groupSql
  }
}
