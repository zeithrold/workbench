package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.workitem.query.WorkItemFieldDefinition
import ink.doa.workbench.core.workitem.query.QueryOperator
import ink.doa.workbench.core.workitem.query.QueryValue

data class SqlFragment(val sql: String, val params: List<Any?> = emptyList()) {
  fun parenthesized(): SqlFragment = copy(sql = "($sql)")
}

data class PostgresWorkItemQueryPlan(
  val fromSql: String,
  val where: SqlFragment,
  val orderBySql: String,
  val params: List<Any?>,
)

sealed interface PostgresWorkItemField {
  val definition: WorkItemFieldDefinition
  val valueSql: String
  val sortSql: String

  data class System(
    override val definition: WorkItemFieldDefinition,
    override val valueSql: String,
    override val sortSql: String = valueSql,
    val predicateCompiler: ((QueryOperator, QueryValue?) -> SqlFragment)? = null,
  ) : PostgresWorkItemField

  data class Property(
    override val definition: WorkItemFieldDefinition,
    override val valueSql: String,
    val identitySql: String,
    val identityParams: List<Any?>,
  ) : PostgresWorkItemField {
    override val sortSql: String = valueSql
  }
}
