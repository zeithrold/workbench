package ink.doa.workbench.data.persistence.postgres.workitem.query

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.query.QueryField
import ink.doa.workbench.core.workitem.query.WorkItemQueryFieldType
import ink.doa.workbench.data.persistence.postgres.toPreparedStatementSetter
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate

class JdbcPostgresWorkItemFieldResolver(
  private val jdbcTemplate: JdbcTemplate,
  private val tenantId: UUID,
) : PostgresWorkItemFieldResolver {
  override fun resolvePostgresField(field: QueryField): PostgresWorkItemField {
    if (field is QueryField.System)
      return StaticPostgresWorkItemFieldResolver().resolvePostgresField(field)
    val property = field as QueryField.Property
    val dataType =
      queryPropertyDataType(property)
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_QUERY_PROPERTY_UNKNOWN,
          "Unknown work item query property: ${field.canonicalName}",
        )
    val type = dataType.toWorkItemFieldType()
    return StaticPostgresWorkItemFieldResolver(
        mapOf((property.apiId ?: property.code).orEmpty() to type)
      )
      .resolvePostgresField(field)
  }

  private fun queryPropertyDataType(property: QueryField.Property): String? {
    val params = propertyLookupParams(property)
    return queryWithParams(propertyLookupSql(property), params) { rs, _ ->
        rs.getString("data_type")
      }
      .singleOrNull()
  }

  private fun <T> queryWithParams(
    sql: String,
    params: List<Any?>,
    rowMapper: (java.sql.ResultSet, Int) -> T,
  ): List<T> = jdbcTemplate.query(sql, params.toPreparedStatementSetter(), rowMapper)

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

private val workItemFieldTypeRegistry: Map<String, WorkItemQueryFieldType> =
  mapOf(
    "text" to WorkItemQueryFieldType.TEXT,
    "long_text" to WorkItemQueryFieldType.LONG_TEXT,
    "number" to WorkItemQueryFieldType.NUMBER,
    "boolean" to WorkItemQueryFieldType.BOOLEAN,
    "date" to WorkItemQueryFieldType.DATE,
    "datetime" to WorkItemQueryFieldType.DATETIME,
    "single_select" to WorkItemQueryFieldType.SINGLE_SELECT,
    "multi_select" to WorkItemQueryFieldType.MULTI_SELECT,
    "user" to WorkItemQueryFieldType.USER,
    "multi_user" to WorkItemQueryFieldType.MULTI_USER,
    "project" to WorkItemQueryFieldType.PROJECT,
    "issue" to WorkItemQueryFieldType.ISSUE,
    "url" to WorkItemQueryFieldType.TEXT,
    "json" to WorkItemQueryFieldType.JSON,
  )

internal fun String.toWorkItemFieldType(): WorkItemQueryFieldType =
  workItemFieldTypeRegistry[this]
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.WORK_ITEM_QUERY_PROPERTY_TYPE_UNSUPPORTED,
      "Unsupported work item property type: $this",
    )
