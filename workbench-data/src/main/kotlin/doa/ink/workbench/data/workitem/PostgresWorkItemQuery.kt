@file:Suppress("TooManyFunctions", "CyclomaticComplexMethod")

package doa.ink.workbench.data.workitem

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.workitem.WorkItemQueryRepository
import doa.ink.workbench.core.workitem.WorkItemSearchPageRequest
import doa.ink.workbench.core.workitem.WorkItemSearchScope
import doa.ink.workbench.core.workitem.model.WorkItemSearchHit
import doa.ink.workbench.core.workitem.model.WorkItemSearchPage
import doa.ink.workbench.core.workitem.model.WorkItemSearchPageInfo
import doa.ink.workbench.core.workitem.model.WorkItemSearchResult
import doa.ink.workbench.core.workitem.query.ConditionNode
import doa.ink.workbench.core.workitem.query.DateDirection
import doa.ink.workbench.core.workitem.query.QueryField
import doa.ink.workbench.core.workitem.query.QueryOperator
import doa.ink.workbench.core.workitem.query.QueryValue
import doa.ink.workbench.core.workitem.query.RelativeDateUnit
import doa.ink.workbench.core.workitem.query.SortDirection
import doa.ink.workbench.core.workitem.query.WorkItemFieldDefinition
import doa.ink.workbench.core.workitem.query.WorkItemQuery
import doa.ink.workbench.core.workitem.query.WorkItemQueryFieldResolver
import doa.ink.workbench.core.workitem.query.WorkItemQueryFieldType
import doa.ink.workbench.core.workitem.query.WorkItemQueryValidator
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import org.postgresql.util.PGobject
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

data class SqlFragment(val sql: String, val params: List<Any?> = emptyList()) {
  fun parenthesized(): SqlFragment = copy(sql = "($sql)")
}

data class PostgresWorkItemQueryPlan(
  val fromSql: String,
  val where: SqlFragment,
  val orderBySql: String,
  val params: List<Any?>,
)

class PostgresWorkItemFilter(
  private val fieldResolver: PostgresWorkItemFieldResolver,
) {
  fun build(scope: WorkItemSearchScope, query: WorkItemQuery): PostgresWorkItemQueryPlan {
    val basePredicates = mutableListOf<SqlFragment>(SqlFragment("i.tenant_id = ?", listOf(scope.tenantId)))
    scope.projectId?.let { basePredicates += SqlFragment("i.project_id = ?", listOf(it)) }
    if (!scope.includeArchived) basePredicates += SqlFragment("i.archived_at IS NULL")
    if (!scope.includeDeleted) basePredicates += SqlFragment("i.deleted_at IS NULL")
    query.where?.let { basePredicates += compileCondition(it) }
    val where = combine("AND", basePredicates)
    return PostgresWorkItemQueryPlan(
      fromSql = FROM_SQL,
      where = where,
      orderBySql = compileOrderBy(query),
      params = where.params,
    )
  }

  private fun compileCondition(node: ConditionNode): SqlFragment =
    when (node) {
      is ConditionNode.And -> combine("AND", node.args.map(::compileCondition))
      is ConditionNode.Or -> combine("OR", node.args.map(::compileCondition))
      is ConditionNode.Not -> {
        val child = compileCondition(node.arg)
        SqlFragment("NOT (${child.sql})", child.params)
      }
      is ConditionNode.Predicate -> compilePredicate(node)
    }

  private fun compilePredicate(predicate: ConditionNode.Predicate): SqlFragment {
    val field = fieldResolver.resolvePostgresField(predicate.field)
    if (field.definition.type == WorkItemQueryFieldType.LONG_TEXT && predicate.op in TEXT_SEARCH_OPERATORS) {
      throw InvalidRequestException("Long text work item predicates require Elasticsearch.")
    }
    val condition = compileOperator(field.valueSql, field.definition.type, predicate.op, predicate.value)
    return when (field) {
      is PostgresWorkItemField.System -> condition
      is PostgresWorkItemField.Property -> {
        val params = mutableListOf<Any?>()
        params.addAll(field.identityParams)
        params.addAll(condition.params)
        SqlFragment(
          """
          EXISTS (
            SELECT 1
            FROM issue_property_values ipv
            JOIN property_definitions pd ON pd.id = ipv.property_id
            LEFT JOIN property_options option_value ON option_value.id = ipv.value_option_id
            LEFT JOIN users user_value ON user_value.id = ipv.value_user_id
            LEFT JOIN projects project_value ON project_value.id = ipv.value_project_id
            LEFT JOIN issues issue_value ON issue_value.id = ipv.value_issue_id
            WHERE ipv.issue_id = i.id
              AND ipv.tenant_id = i.tenant_id
              AND ${field.identitySql}
              AND ${condition.sql}
          )
          """
            .trimIndent(),
          params,
        )
      }
    }
  }

  private fun compileOperator(
    valueSql: String,
    type: WorkItemQueryFieldType,
    op: QueryOperator,
    value: QueryValue?,
  ): SqlFragment =
    when (op) {
      QueryOperator.EQ -> compare(valueSql, "=", value)
      QueryOperator.NEQ -> compare(valueSql, "<>", value)
      QueryOperator.IN -> arrayCompare(valueSql, "IN", value)
      QueryOperator.NOT_IN -> arrayCompare(valueSql, "NOT IN", value)
      QueryOperator.LT,
      QueryOperator.BEFORE -> compare(valueSql, "<", value)
      QueryOperator.LTE,
      QueryOperator.ON_OR_BEFORE -> compare(valueSql, "<=", value)
      QueryOperator.GT,
      QueryOperator.AFTER -> compare(valueSql, ">", value)
      QueryOperator.GTE,
      QueryOperator.ON_OR_AFTER -> compare(valueSql, ">=", value)
      QueryOperator.BETWEEN -> between(valueSql, value)
      QueryOperator.CONTAINS -> contains(valueSql, type, value, negated = false)
      QueryOperator.NOT_CONTAINS -> contains(valueSql, type, value, negated = true)
      QueryOperator.STARTS_WITH -> like(valueSql, value, suffix = "%")
      QueryOperator.ENDS_WITH -> like(valueSql, value, prefix = "%")
      QueryOperator.MATCHES -> regex(valueSql, value)
      QueryOperator.IS_EMPTY -> emptyCheck(valueSql, type, negated = false)
      QueryOperator.IS_NOT_EMPTY -> emptyCheck(valueSql, type, negated = true)
      QueryOperator.WITHIN -> within(valueSql, value)
      QueryOperator.HAS_ANY -> jsonbArray(valueSql, "?|", value)
      QueryOperator.HAS_ALL -> jsonbArray(valueSql, "?&", value)
      QueryOperator.HAS_NONE -> {
        val fragment = jsonbArray(valueSql, "?|", value)
        SqlFragment("NOT (${fragment.sql})", fragment.params)
      }
    }

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
    val range = value as? QueryValue.Between
      ?: throw InvalidRequestException("Operator between requires an object value.")
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
      if (type == WorkItemQueryFieldType.JSON || type == WorkItemQueryFieldType.MULTI_SELECT || type == WorkItemQueryFieldType.MULTI_USER) {
        val operand = value.toOperand(jsonb = true)
        SqlFragment("$valueSql @> ${operand.sql}", operand.params)
      } else {
        val literal = value.requireStringLiteral()
        SqlFragment("$valueSql ILIKE ?", listOf("%${escapeLike(literal)}%"))
      }
    return if (negated) SqlFragment("NOT (${fragment.sql})", fragment.params) else fragment
  }

  private fun like(valueSql: String, value: QueryValue?, prefix: String = "", suffix: String = ""): SqlFragment {
    val literal = value.requireStringLiteral()
    return SqlFragment("$valueSql ILIKE ?", listOf("$prefix${escapeLike(literal)}$suffix"))
  }

  private fun regex(valueSql: String, value: QueryValue?): SqlFragment =
    SqlFragment("$valueSql ~* ?", listOf(value.requireStringLiteral()))

  private fun emptyCheck(valueSql: String, type: WorkItemQueryFieldType, negated: Boolean): SqlFragment {
    val expression =
      if (type == WorkItemQueryFieldType.JSON || type == WorkItemQueryFieldType.MULTI_SELECT || type == WorkItemQueryFieldType.MULTI_USER) {
        "($valueSql IS NULL OR $valueSql = '[]'::jsonb OR $valueSql = '{}'::jsonb)"
      } else {
        "($valueSql IS NULL OR $valueSql::text = '')"
      }
    return if (negated) SqlFragment("NOT $expression") else SqlFragment(expression)
  }

  private fun within(valueSql: String, value: QueryValue?): SqlFragment {
    val relative = value as? QueryValue.RelativeDate
      ?: throw InvalidRequestException("Operator within requires a relativeDate value.")
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

  private fun compileOrderBy(query: WorkItemQuery): String {
    if (query.sort.isEmpty()) return "ORDER BY i.updated_at DESC, i.api_id ASC"
    val terms =
      query.sort.map { term ->
        val field = fieldResolver.resolvePostgresField(term.field)
        if (!field.definition.sortable) {
          throw InvalidRequestException("Field ${term.field.canonicalName} is not sortable.")
        }
        val direction = if (term.direction == SortDirection.ASC) "ASC" else "DESC"
        val nulls = term.nulls?.wireName?.uppercase()?.let { " NULLS $it" } ?: ""
        "${field.sortSql} $direction$nulls"
      }
    return "ORDER BY ${terms.joinToString(", ")}, i.api_id ASC"
  }

  private fun combine(operator: String, fragments: List<SqlFragment>): SqlFragment {
    val params = fragments.flatMap { it.params }
    return SqlFragment(fragments.joinToString(" $operator ") { "(${it.sql})" }, params)
  }

  companion object {
    private val TEXT_SEARCH_OPERATORS =
      setOf(QueryOperator.CONTAINS, QueryOperator.NOT_CONTAINS, QueryOperator.MATCHES)

    private const val FROM_SQL =
      """
      FROM issues i
      JOIN projects p ON p.id = i.project_id
      JOIN issue_types itype ON itype.id = i.issue_type_id
      JOIN issue_type_configs itc ON itc.id = i.issue_type_config_id
      JOIN issue_statuses st ON st.id = i.status_id
      LEFT JOIN priorities pri ON pri.id = i.priority_id
      JOIN users reporter ON reporter.id = i.reporter_id
      LEFT JOIN users assignee ON assignee.id = i.assignee_id
      LEFT JOIN users created_by_user ON created_by_user.id = i.created_by
      LEFT JOIN users updated_by_user ON updated_by_user.id = i.updated_by
      LEFT JOIN sprints sprint ON sprint.id = i.sprint_id
      LEFT JOIN issue_key_aliases keya ON keya.issue_id = i.id AND keya.is_current = true
      """
  }
}

sealed interface PostgresWorkItemField {
  val definition: WorkItemFieldDefinition
  val valueSql: String
  val sortSql: String

  data class System(
    override val definition: WorkItemFieldDefinition,
    override val valueSql: String,
    override val sortSql: String = valueSql,
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

interface PostgresWorkItemFieldResolver : WorkItemQueryFieldResolver {
  fun resolvePostgresField(field: QueryField): PostgresWorkItemField

  override fun resolve(field: QueryField): WorkItemFieldDefinition = resolvePostgresField(field).definition
}

class StaticPostgresWorkItemFieldResolver(
  private val propertyTypes: Map<String, WorkItemQueryFieldType> = emptyMap(),
) : PostgresWorkItemFieldResolver {
  override fun resolvePostgresField(field: QueryField): PostgresWorkItemField =
    when (field) {
      is QueryField.System -> systemField(field)
      is QueryField.Property -> propertyField(field, propertyTypes[field.apiId ?: field.code])
    }

  private fun systemField(field: QueryField.System): PostgresWorkItemField.System =
    SYSTEM_FIELDS[field.canonicalName]
      ?: throw InvalidRequestException("Unknown work item query field: ${field.canonicalName}")

  private fun propertyField(
    field: QueryField.Property,
    type: WorkItemQueryFieldType?,
  ): PostgresWorkItemField.Property {
    val resolvedType = type ?: WorkItemQueryFieldType.JSON
    val identityParts = mutableListOf<String>()
    val identityParams = mutableListOf<Any?>()
    field.apiId?.let {
      identityParts += "pd.api_id = ?"
      identityParams += it
    }
    field.code?.let {
      identityParts += "pd.code = ?"
      identityParams += it
    }
    return PostgresWorkItemField.Property(
      definition = WorkItemFieldDefinition(field, resolvedType, sortable = resolvedType in SORTABLE_PROPERTY_TYPES),
      valueSql = propertyValueColumn(resolvedType),
      identitySql = identityParts.joinToString(" AND "),
      identityParams = identityParams,
    )
  }

  companion object {
    private val SORTABLE_PROPERTY_TYPES =
      setOf(
        WorkItemQueryFieldType.TEXT,
        WorkItemQueryFieldType.NUMBER,
        WorkItemQueryFieldType.BOOLEAN,
        WorkItemQueryFieldType.DATE,
        WorkItemQueryFieldType.DATETIME,
        WorkItemQueryFieldType.SINGLE_SELECT,
        WorkItemQueryFieldType.USER,
        WorkItemQueryFieldType.PROJECT,
      )

    private val SYSTEM_FIELDS =
      mapOf(
        "id" to system("id", WorkItemQueryFieldType.ID, "i.id", sortable = false),
        "apiId" to system("apiId", WorkItemQueryFieldType.TEXT, "i.api_id", sortable = false),
        "key" to
          system(
            "key",
            WorkItemQueryFieldType.TEXT,
            "COALESCE(keya.issue_key, p.identifier || '-' || i.sequence_no)",
            sortable = true,
          ),
        "tenant" to system("tenant", WorkItemQueryFieldType.ID, "i.tenant_id", sortable = false),
        "project" to system("project", WorkItemQueryFieldType.PROJECT, "p.api_id", sortable = false),
        "issueType" to system("issueType", WorkItemQueryFieldType.SINGLE_SELECT, "itype.api_id", sortable = true),
        "title" to system("title", WorkItemQueryFieldType.TEXT, "i.title", sortable = true),
        "description" to system("description", WorkItemQueryFieldType.LONG_TEXT, "i.description", sortable = false),
        "status" to system("status", WorkItemQueryFieldType.SINGLE_SELECT, "st.api_id", sortable = true),
        "statusGroup" to system("statusGroup", WorkItemQueryFieldType.SINGLE_SELECT, "st.status_group", sortable = true),
        "priority" to system("priority", WorkItemQueryFieldType.SINGLE_SELECT, "pri.api_id", sortable = true),
        "reporter" to system("reporter", WorkItemQueryFieldType.USER, "reporter.api_id", sortable = true),
        "assignee" to system("assignee", WorkItemQueryFieldType.USER, "assignee.api_id", sortable = true),
        "sprint" to system("sprint", WorkItemQueryFieldType.ID, "sprint.api_id", sortable = true),
        "createdBy" to system("createdBy", WorkItemQueryFieldType.USER, "created_by_user.api_id", sortable = false),
        "updatedBy" to system("updatedBy", WorkItemQueryFieldType.USER, "updated_by_user.api_id", sortable = false),
        "createdAt" to system("createdAt", WorkItemQueryFieldType.DATETIME, "i.created_at", sortable = true),
        "updatedAt" to system("updatedAt", WorkItemQueryFieldType.DATETIME, "i.updated_at", sortable = true),
        "archivedAt" to system("archivedAt", WorkItemQueryFieldType.DATETIME, "i.archived_at", sortable = false),
        "deletedAt" to system("deletedAt", WorkItemQueryFieldType.DATETIME, "i.deleted_at", sortable = false),
        "parent" to
          system(
            "parent",
            WorkItemQueryFieldType.ISSUE,
            """
            (
              SELECT parent_issue.api_id
              FROM issue_hierarchy ih
              JOIN issues parent_issue ON parent_issue.id = ih.parent_issue_id
              WHERE ih.child_issue_id = i.id
            )
            """
              .trimIndent(),
            sortable = false,
          ),
        "children.count" to
          system(
            "children.count",
            WorkItemQueryFieldType.NUMBER,
            "(SELECT COUNT(*) FROM issue_hierarchy child_ih WHERE child_ih.parent_issue_id = i.id)",
            sortable = false,
          ),
      )

    private fun system(
      name: String,
      type: WorkItemQueryFieldType,
      valueSql: String,
      sortable: Boolean,
    ) =
      PostgresWorkItemField.System(
        definition = WorkItemFieldDefinition(QueryField.System(name), type, sortable),
        valueSql = valueSql,
      )

    private fun propertyValueColumn(type: WorkItemQueryFieldType): String =
      when (type) {
        WorkItemQueryFieldType.TEXT,
        WorkItemQueryFieldType.LONG_TEXT -> "ipv.value_text"
        WorkItemQueryFieldType.NUMBER -> "ipv.value_number"
        WorkItemQueryFieldType.BOOLEAN -> "ipv.value_boolean"
        WorkItemQueryFieldType.DATE -> "ipv.value_date"
        WorkItemQueryFieldType.DATETIME -> "ipv.value_datetime"
        WorkItemQueryFieldType.SINGLE_SELECT -> "option_value.api_id"
        WorkItemQueryFieldType.MULTI_SELECT,
        WorkItemQueryFieldType.MULTI_USER,
        WorkItemQueryFieldType.JSON -> "ipv.value_array"
        WorkItemQueryFieldType.USER -> "user_value.api_id"
        WorkItemQueryFieldType.PROJECT -> "project_value.api_id"
        WorkItemQueryFieldType.ISSUE -> "issue_value.api_id"
        WorkItemQueryFieldType.ID -> "ipv.value_text"
      }
  }
}

@Repository
class JdbcWorkItemQueryRepository(private val jdbcTemplate: JdbcTemplate) : WorkItemQueryRepository {
  override suspend fun search(
    scope: WorkItemSearchScope,
    query: WorkItemQuery,
    page: WorkItemSearchPageRequest,
  ): WorkItemSearchPage =
    withContext(Dispatchers.IO) {
      val resolver = JdbcPostgresWorkItemFieldResolver(jdbcTemplate, scope.tenantId)
      WorkItemQueryValidator(resolver).validate(query)
      val plan = PostgresWorkItemFilter(resolver).build(scope, query)
      val selectSql =
        """
        SELECT
          i.api_id,
          COALESCE(keya.issue_key, p.identifier || '-' || i.sequence_no) AS issue_key,
          i.title,
          i.description,
          p.api_id AS project_api_id,
          itype.api_id AS issue_type_api_id,
          itc.api_id AS issue_type_config_api_id,
          st.api_id AS status_api_id,
          st.status_group,
          pri.api_id AS priority_api_id,
          reporter.api_id AS reporter_api_id,
          assignee.api_id AS assignee_api_id,
          sprint.api_id AS sprint_api_id,
          i.created_at,
          i.updated_at,
          i.properties_snapshot::text AS properties_snapshot
        ${plan.fromSql}
        WHERE ${plan.where.sql}
        ${plan.orderBySql}
        LIMIT ? OFFSET ?
        """
          .trimIndent()
      val params = plan.params + listOf(page.limit, page.offset)
      val hits = jdbcTemplate.query(selectSql, WorkItemSearchHitRowMapper, *params.toTypedArray())
      val countSql = "SELECT COUNT(*) ${plan.fromSql} WHERE ${plan.where.sql}"
      val total = jdbcTemplate.queryForObject(countSql, Long::class.java, *plan.params.toTypedArray())
      WorkItemSearchPage(
        result = WorkItemSearchResult(hits = hits, total = total),
        page =
          WorkItemSearchPageInfo(
            limit = page.limit,
            offset = page.offset,
            nextOffset = if (hits.size == page.limit) page.offset + page.limit else null,
          ),
      )
    }
}

private class JdbcPostgresWorkItemFieldResolver(
  private val jdbcTemplate: JdbcTemplate,
  private val tenantId: UUID,
) : PostgresWorkItemFieldResolver {
  override fun resolvePostgresField(field: QueryField): PostgresWorkItemField {
    if (field is QueryField.System) return StaticPostgresWorkItemFieldResolver().resolvePostgresField(field)
    val property = field as QueryField.Property
    val dataType =
      jdbcTemplate.query(
        propertyLookupSql(property),
        { rs, _ -> rs.getString("data_type") },
        *propertyLookupParams(property).toTypedArray(),
      )
        .singleOrNull()
        ?: throw InvalidRequestException("Unknown work item query property: ${field.canonicalName}")
    val type = dataType.toWorkItemFieldType()
    return StaticPostgresWorkItemFieldResolver(mapOf((property.apiId ?: property.code).orEmpty() to type))
      .resolvePostgresField(field)
  }

  private fun propertyLookupSql(property: QueryField.Property): String {
    val identitySql =
      when {
        property.apiId != null && property.code != null -> "(api_id = ? OR code = ?)"
        property.apiId != null -> "api_id = ?"
        else -> "code = ?"
      }
    return "SELECT data_type FROM property_definitions WHERE tenant_id = ? AND is_active = true AND $identitySql"
  }

  private fun propertyLookupParams(property: QueryField.Property): List<Any?> =
    listOf(tenantId) +
      listOfNotNull(
        property.apiId,
        property.code,
      )
}

object WorkItemSearchHitRowMapper : RowMapper<WorkItemSearchHit> {
  private val json = Json { ignoreUnknownKeys = true }

  override fun mapRow(rs: ResultSet, rowNum: Int): WorkItemSearchHit =
    WorkItemSearchHit(
      apiId = rs.getString("api_id"),
      key = rs.getString("issue_key"),
      title = rs.getString("title"),
      description = rs.getString("description"),
      projectApiId = rs.getString("project_api_id"),
      issueTypeApiId = rs.getString("issue_type_api_id"),
      issueTypeConfigApiId = rs.getString("issue_type_config_api_id"),
      statusApiId = rs.getString("status_api_id"),
      statusGroup = rs.getString("status_group"),
      priorityApiId = rs.getString("priority_api_id"),
      reporterApiId = rs.getString("reporter_api_id"),
      assigneeApiId = rs.getString("assignee_api_id"),
      sprintApiId = rs.getString("sprint_api_id"),
      createdAt = rs.getObject("created_at", OffsetDateTime::class.java) ?: rs.getTimestamp("created_at").toOffsetDateTime(),
      updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java) ?: rs.getTimestamp("updated_at").toOffsetDateTime(),
      properties = parseProperties(rs.getString("properties_snapshot")),
    )

  private fun parseProperties(value: String?): JsonObject =
    value?.let { json.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap())

  private fun Timestamp.toOffsetDateTime(): OffsetDateTime = toInstant().atOffset(java.time.ZoneOffset.UTC)
}

private fun QueryValue?.toOperand(jsonb: Boolean = false): SqlFragment =
  when (this) {
    is QueryValue.Variable -> toSqlVariable()
    is QueryValue.Literal -> {
      val jdbcValue = if (jsonb) jsonToJsonb(value) else jsonToJdbcValue(value)
      SqlFragment("?", listOf(jdbcValue))
    }
    else -> throw InvalidRequestException("Operator requires a single value.")
  }

private fun QueryValue?.asLiteralArray(): List<JsonElement> {
  val literal = this as? QueryValue.Literal
    ?: throw InvalidRequestException("Operator requires an array value.")
  val array = literal.value as? JsonArray
    ?: throw InvalidRequestException("Operator requires an array value.")
  return array
}

private fun QueryValue?.requireStringLiteral(): String {
  val literal = this as? QueryValue.Literal
    ?: throw InvalidRequestException("Operator requires a string value.")
  return (literal.value as? JsonPrimitive)?.contentOrNull
    ?: throw InvalidRequestException("Operator requires a string value.")
}

private fun QueryValue.Variable.toSqlVariable(): SqlFragment =
  when (name) {
    "date.now" -> SqlFragment("now()")
    "date.today" -> SqlFragment("current_date")
    "date.startOfWeek" -> SqlFragment("date_trunc('week', now())")
    "date.endOfWeek" -> SqlFragment("date_trunc('week', now()) + interval '6 days'")
    else -> throw InvalidRequestException("Variable $name requires trusted request context binding.")
  }

private fun jsonToJdbcValue(element: JsonElement): Any? =
  when (element) {
    is JsonNull -> null
    is JsonPrimitive ->
      element.booleanOrNull ?: element.doubleOrNull ?: element.contentOrNull
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
    else -> throw InvalidRequestException("Unknown relative date anchor: $this")
  }

private fun RelativeDateUnit.toPostgresInterval(): String =
  when (this) {
    RelativeDateUnit.DAY -> "1 day"
    RelativeDateUnit.WEEK -> "1 week"
    RelativeDateUnit.MONTH -> "1 month"
    RelativeDateUnit.YEAR -> "1 year"
  }

private fun String.toWorkItemFieldType(): WorkItemQueryFieldType =
  when (this) {
    "text" -> WorkItemQueryFieldType.TEXT
    "long_text" -> WorkItemQueryFieldType.LONG_TEXT
    "number" -> WorkItemQueryFieldType.NUMBER
    "boolean" -> WorkItemQueryFieldType.BOOLEAN
    "date" -> WorkItemQueryFieldType.DATE
    "datetime" -> WorkItemQueryFieldType.DATETIME
    "single_select" -> WorkItemQueryFieldType.SINGLE_SELECT
    "multi_select" -> WorkItemQueryFieldType.MULTI_SELECT
    "user" -> WorkItemQueryFieldType.USER
    "multi_user" -> WorkItemQueryFieldType.MULTI_USER
    "project" -> WorkItemQueryFieldType.PROJECT
    "issue" -> WorkItemQueryFieldType.ISSUE
    "url" -> WorkItemQueryFieldType.TEXT
    "json" -> WorkItemQueryFieldType.JSON
    else -> throw InvalidRequestException("Unsupported work item property type: $this")
  }
