package one.ztd.workbench.data.persistence.postgres.workitem.query

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import one.ztd.workbench.agile.workitem.query.DateDirection
import one.ztd.workbench.agile.workitem.query.QueryOperator
import one.ztd.workbench.agile.workitem.query.QueryValue
import one.ztd.workbench.agile.workitem.query.RelativeDateUnit
import one.ztd.workbench.agile.workitem.query.WorkItemQueryFieldType
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import org.postgresql.util.PGobject

class PostgresWorkItemOperatorCompiler {
  private data class CompileContext(
    val valueSql: String,
    val type: WorkItemQueryFieldType,
    val value: QueryValue?,
  )

  private val operatorCompilers: Map<QueryOperator, (CompileContext) -> SqlFragment> =
    mapOf(
      QueryOperator.EQ to { ctx -> compare(ctx.valueSql, "=", ctx.value) },
      QueryOperator.NEQ to { ctx -> compare(ctx.valueSql, "<>", ctx.value) },
      QueryOperator.IN to { ctx -> arrayCompare(ctx.valueSql, "IN", ctx.value) },
      QueryOperator.NOT_IN to { ctx -> arrayCompare(ctx.valueSql, "NOT IN", ctx.value) },
      QueryOperator.LT to { ctx -> compare(ctx.valueSql, "<", ctx.value) },
      QueryOperator.BEFORE to { ctx -> compare(ctx.valueSql, "<", ctx.value) },
      QueryOperator.LTE to { ctx -> compare(ctx.valueSql, "<=", ctx.value) },
      QueryOperator.ON_OR_BEFORE to { ctx -> compare(ctx.valueSql, "<=", ctx.value) },
      QueryOperator.GT to { ctx -> compare(ctx.valueSql, ">", ctx.value) },
      QueryOperator.AFTER to { ctx -> compare(ctx.valueSql, ">", ctx.value) },
      QueryOperator.GTE to { ctx -> compare(ctx.valueSql, ">=", ctx.value) },
      QueryOperator.ON_OR_AFTER to { ctx -> compare(ctx.valueSql, ">=", ctx.value) },
      QueryOperator.BETWEEN to { ctx -> between(ctx.valueSql, ctx.value) },
      QueryOperator.CONTAINS to
        { ctx ->
          contains(ctx.valueSql, ctx.type, ctx.value, negated = false)
        },
      QueryOperator.NOT_CONTAINS to
        { ctx ->
          contains(ctx.valueSql, ctx.type, ctx.value, negated = true)
        },
      QueryOperator.STARTS_WITH to { ctx -> like(ctx.valueSql, ctx.value, suffix = "%") },
      QueryOperator.ENDS_WITH to { ctx -> like(ctx.valueSql, ctx.value, prefix = "%") },
      QueryOperator.MATCHES to { ctx -> regex(ctx.valueSql, ctx.value) },
      QueryOperator.IS_EMPTY to { ctx -> emptyCheck(ctx.valueSql, ctx.type, negated = false) },
      QueryOperator.IS_NOT_EMPTY to { ctx -> emptyCheck(ctx.valueSql, ctx.type, negated = true) },
      QueryOperator.WITHIN to { ctx -> within(ctx.valueSql, ctx.value) },
      QueryOperator.HAS_ANY to { ctx -> jsonbArray(ctx.valueSql, "?|", ctx.value) },
      QueryOperator.HAS_ALL to { ctx -> jsonbArray(ctx.valueSql, "?&", ctx.value) },
      QueryOperator.HAS_NONE to
        { ctx ->
          val fragment = jsonbArray(ctx.valueSql, "?|", ctx.value)
          SqlFragment("NOT (${fragment.sql})", fragment.params)
        },
    )

  fun compile(
    valueSql: String,
    type: WorkItemQueryFieldType,
    op: QueryOperator,
    value: QueryValue?,
  ): SqlFragment = operatorCompilers.getValue(op)(CompileContext(valueSql, type, value))

  private fun compare(valueSql: String, operator: String, value: QueryValue?): SqlFragment {
    val operand = value.toOperand()
    return SqlFragment("$valueSql $operator ${operand.sql}", operand.params)
  }

  private fun arrayCompare(valueSql: String, operator: String, value: QueryValue?): SqlFragment {
    val values = value.asLiteralArray()
    val placeholders = values.joinToString(", ") { "?" }
    return SqlFragment("$valueSql $operator ($placeholders)", values.map(::jsonToJdbcValue))
  }

  private fun between(valueSql: String, value: QueryValue?): SqlFragment {
    val range =
      value as? QueryValue.Between
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_BETWEEN_OBJECT_REQUIRED
        )
    val parts = mutableListOf<String>()
    val params = mutableListOf<Any?>()
    range.from?.let {
      parts += "$valueSql >= ?"
      params += jsonToJdbcValue(it)
    }
    range.to?.let {
      parts += "$valueSql <= ?"
      params += jsonToJdbcValue(it)
    }
    return SqlFragment(parts.joinToString(" AND "), params)
  }

  private fun contains(
    valueSql: String,
    type: WorkItemQueryFieldType,
    value: QueryValue?,
    negated: Boolean,
  ): SqlFragment {
    val fragment =
      if (
        type == WorkItemQueryFieldType.JSON ||
          type == WorkItemQueryFieldType.MULTI_SELECT ||
          type == WorkItemQueryFieldType.MULTI_USER
      ) {
        val operand = value.toOperand(jsonb = true)
        SqlFragment("$valueSql @> ${operand.sql}", operand.params)
      } else {
        val literal = value.requireStringLiteral()
        SqlFragment("$valueSql ILIKE ?", listOf("%${escapeLike(literal)}%"))
      }
    return if (negated) SqlFragment("NOT (${fragment.sql})", fragment.params) else fragment
  }

  private fun like(
    valueSql: String,
    value: QueryValue?,
    prefix: String = "",
    suffix: String = "",
  ): SqlFragment {
    val literal = value.requireStringLiteral()
    return SqlFragment("$valueSql ILIKE ?", listOf("$prefix${escapeLike(literal)}$suffix"))
  }

  private fun regex(valueSql: String, value: QueryValue?): SqlFragment =
    SqlFragment("$valueSql ~* ?", listOf(value.requireStringLiteral()))

  private fun emptyCheck(
    valueSql: String,
    type: WorkItemQueryFieldType,
    negated: Boolean,
  ): SqlFragment {
    val expression =
      if (
        type == WorkItemQueryFieldType.JSON ||
          type == WorkItemQueryFieldType.MULTI_SELECT ||
          type == WorkItemQueryFieldType.MULTI_USER
      ) {
        "($valueSql IS NULL OR $valueSql = '[]'::jsonb OR $valueSql = '{}'::jsonb)"
      } else {
        "($valueSql IS NULL OR $valueSql::text = '')"
      }
    return if (negated) SqlFragment("NOT $expression") else SqlFragment(expression)
  }

  private fun within(valueSql: String, value: QueryValue?): SqlFragment {
    val relative =
      value as? QueryValue.RelativeDate
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_RELATIVE_DATE_REQUIRED
        )
    val anchor = relative.anchor.toPostgresAnchor()
    val interval = relative.unit.toPostgresInterval()
    return when (relative.direction) {
      DateDirection.PAST ->
        SqlFragment(
          "$valueSql >= ($anchor - (? * interval '$interval')) AND $valueSql <= $anchor",
          listOf(relative.amount),
        )
      DateDirection.FUTURE ->
        SqlFragment(
          "$valueSql >= $anchor AND $valueSql <= ($anchor + (? * interval '$interval'))",
          listOf(relative.amount),
        )
    }
  }

  private fun jsonbArray(valueSql: String, operator: String, value: QueryValue?): SqlFragment {
    val values = value.asLiteralArray().map { it.asStringForSqlArray() }.toTypedArray()
    return SqlFragment("$valueSql $operator ?", listOf(values))
  }
}

internal fun QueryValue?.toOperand(jsonb: Boolean = false): SqlFragment =
  when (this) {
    is QueryValue.Variable -> toSqlVariable()
    is QueryValue.Literal -> {
      val jdbcValue = if (jsonb) jsonToJsonb(value) else jsonToJdbcValue(value)
      SqlFragment("?", listOf(jdbcValue))
    }
    else ->
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_SINGLE_VALUE_REQUIRED
      )
  }

internal fun QueryValue?.asLiteralArray(): List<JsonElement> {
  val literal =
    this as? QueryValue.Literal
      ?: throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_ARRAY_REQUIRED)
  val array =
    literal.value as? JsonArray
      ?: throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_ARRAY_REQUIRED)
  return array
}

internal fun QueryValue?.requireStringLiteral(): String {
  val literal =
    this as? QueryValue.Literal
      ?: throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_STRING_REQUIRED)
  return (literal.value as? JsonPrimitive)?.contentOrNull
    ?: throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_QUERY_OPERATOR_STRING_REQUIRED)
}

private fun QueryValue.Variable.toSqlVariable(): SqlFragment =
  when (name) {
    "date.now" -> SqlFragment("now()")
    "date.today" -> SqlFragment("current_date")
    "date.startOfWeek" -> SqlFragment("date_trunc('week', now())")
    "date.endOfWeek" -> SqlFragment("date_trunc('week', now()) + interval '6 days'")
    else ->
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_VARIABLE_TRUSTED_CONTEXT_REQUIRED,
        "Variable $name requires trusted request context binding.",
      )
  }

internal fun jsonToJdbcValue(element: JsonElement): Any? =
  when (element) {
    is JsonNull -> null
    is JsonPrimitive -> element.booleanOrNull ?: element.doubleOrNull ?: element.contentOrNull
    else -> jsonToJsonb(element)
  }

private fun jsonToJsonb(element: JsonElement): PGobject =
  PGobject().apply {
    type = "jsonb"
    value = element.toString()
  }

private fun JsonElement.asStringForSqlArray(): String =
  (this as? JsonPrimitive)?.contentOrNull ?: toString()

private fun escapeLike(value: String): String =
  value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

private fun String.toPostgresAnchor(): String =
  when (this) {
    "date.now" -> "now()"
    "date.today" -> "current_date"
    "date.startOfWeek" -> "date_trunc('week', now())"
    "date.endOfWeek" -> "date_trunc('week', now()) + interval '6 days'"
    else ->
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_RELATIVE_DATE_ANCHOR_UNKNOWN,
        "Unknown relative date anchor: $this",
      )
  }

private fun RelativeDateUnit.toPostgresInterval(): String =
  when (this) {
    RelativeDateUnit.DAY -> "1 day"
    RelativeDateUnit.WEEK -> "1 week"
    RelativeDateUnit.MONTH -> "1 month"
    RelativeDateUnit.YEAR -> "1 year"
  }
