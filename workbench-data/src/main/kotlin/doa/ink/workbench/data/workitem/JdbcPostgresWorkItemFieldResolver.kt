package doa.ink.workbench.data.workitem

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.workitem.query.QueryField
import doa.ink.workbench.core.workitem.query.WorkItemQueryFieldType
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
      jdbcTemplate
        .query(
          propertyLookupSql(property),
          { rs, _ -> rs.getString("data_type") },
          *propertyLookupParams(property).toTypedArray(),
        )
        .singleOrNull()
        ?: throw InvalidRequestException("Unknown work item query property: ${field.canonicalName}")
    val type = dataType.toWorkItemFieldType()
    return StaticPostgresWorkItemFieldResolver(
        mapOf((property.apiId ?: property.code).orEmpty() to type)
      )
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

@Suppress("CyclomaticComplexMethod")
internal fun String.toWorkItemFieldType(): WorkItemQueryFieldType =
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
