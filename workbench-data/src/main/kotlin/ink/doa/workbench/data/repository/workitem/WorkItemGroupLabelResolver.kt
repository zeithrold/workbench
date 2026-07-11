package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.core.workitem.query.QueryField
import ink.doa.workbench.core.workitem.query.QueryOperator
import ink.doa.workbench.core.workitem.query.WorkItemGroupKey
import ink.doa.workbench.core.workitem.query.WorkItemGroupKeySupport
import ink.doa.workbench.core.workitem.query.WorkItemGroupLabel
import ink.doa.workbench.core.workitem.query.WorkItemGroupLabelCode
import ink.doa.workbench.core.workitem.query.WorkItemQueryFieldType
import ink.doa.workbench.data.persistence.postgres.toPreparedStatementSetter
import ink.doa.workbench.data.persistence.postgres.workitem.query.JdbcPostgresWorkItemFieldResolver
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class WorkItemGroupLabelResolver(private val jdbcTemplate: JdbcTemplate) {
  fun resolve(tenantId: UUID, groupKey: WorkItemGroupKey): WorkItemGroupLabel {
    if (groupKey.op == QueryOperator.IS_EMPTY) {
      return emptyLabelFor(tenantId, groupKey.field)
    }
    val bucketValue =
      WorkItemGroupKeySupport.bucketValueFromKey(groupKey)
        ?: return WorkItemGroupLabelCode.EMPTY_GENERIC.toLabel()
    return when (val field = groupKey.field) {
      is QueryField.System -> resolveSystemLabel(tenantId, field, bucketValue)
      is QueryField.Property -> resolvePropertyLabel(tenantId, field, bucketValue)
    }
  }

  private fun emptyLabelFor(tenantId: UUID, field: QueryField): WorkItemGroupLabel.Message =
    when (field) {
      is QueryField.System ->
        when (field.canonicalName) {
          "assignee" -> WorkItemGroupLabelCode.EMPTY_ASSIGNEE.toLabel()
          "priority" -> WorkItemGroupLabelCode.EMPTY_PRIORITY.toLabel()
          "sprint" -> WorkItemGroupLabelCode.EMPTY_SPRINT.toLabel()
          else -> WorkItemGroupLabelCode.EMPTY_GENERIC.toLabel()
        }
      is QueryField.Property -> emptyPropertyLabel(tenantId, field)
    }

  private fun emptyPropertyLabel(
    tenantId: UUID,
    field: QueryField.Property,
  ): WorkItemGroupLabel.Message {
    val propertyType = propertyFieldType(tenantId, field)
    val propertyName = lookupPropertyName(tenantId, field)
    val args = mapOf("propertyName" to propertyName)
    return when (propertyType) {
      WorkItemQueryFieldType.USER -> WorkItemGroupLabelCode.EMPTY_PROPERTY_USER.toLabel(args)
      WorkItemQueryFieldType.SINGLE_SELECT ->
        WorkItemGroupLabelCode.EMPTY_PROPERTY_OPTION.toLabel(args)
      else -> WorkItemGroupLabelCode.EMPTY_GENERIC.toLabel()
    }
  }

  private fun resolveSystemLabel(
    tenantId: UUID,
    field: QueryField.System,
    bucketValue: Any,
  ): WorkItemGroupLabel {
    val value = bucketValue.toString()
    val text =
      when (field.canonicalName) {
        "status" ->
          lookupName(
            "SELECT name FROM issue_statuses WHERE tenant_id = ? AND api_id = ?",
            listOf(tenantId, value),
          )
        "priority" ->
          lookupName(
            "SELECT name FROM priorities WHERE tenant_id = ? AND api_id = ?",
            listOf(tenantId, value),
          )
        "issueType" ->
          lookupName(
            "SELECT name FROM issue_types WHERE tenant_id = ? AND api_id = ?",
            listOf(tenantId, value),
          )
        "assignee",
        "reporter" -> lookupName("SELECT display_name FROM users WHERE api_id = ?", listOf(value))
        "sprint" ->
          lookupName(
            "SELECT name FROM sprints WHERE tenant_id = ? AND api_id = ?",
            listOf(tenantId, value),
          )
        "statusGroup" -> value.replace('_', ' ').replaceFirstChar { it.uppercase() }
        else -> value
      }
    return WorkItemGroupLabel.Text(text)
  }

  private fun resolvePropertyLabel(
    tenantId: UUID,
    field: QueryField.Property,
    bucketValue: Any,
  ): WorkItemGroupLabel {
    val value = bucketValue.toString()
    val text =
      when (propertyFieldType(tenantId, field)) {
        WorkItemQueryFieldType.SINGLE_SELECT ->
          lookupName(
            "SELECT label FROM property_options WHERE tenant_id = ? AND api_id = ?",
            listOf(tenantId, value),
          )
        WorkItemQueryFieldType.USER ->
          lookupName("SELECT display_name FROM users WHERE api_id = ?", listOf(value))
        else -> value
      }
    return WorkItemGroupLabel.Text(text)
  }

  private fun propertyFieldType(
    tenantId: UUID,
    field: QueryField.Property,
  ): WorkItemQueryFieldType =
    JdbcPostgresWorkItemFieldResolver(jdbcTemplate, tenantId)
      .resolvePostgresField(field)
      .definition
      .type

  private fun lookupPropertyName(tenantId: UUID, field: QueryField.Property): String {
    val params = propertyLookupParams(tenantId, field)
    return lookupName(propertyNameSql(field), params)
  }

  private fun propertyNameSql(field: QueryField.Property): String {
    val identitySql =
      when {
        field.apiId != null && field.code != null -> "(api_id = ? OR code = ?)"
        field.apiId != null -> "api_id = ?"
        else -> "code = ?"
      }
    return "SELECT name FROM property_definitions WHERE tenant_id = ? AND is_active = true AND $identitySql"
  }

  private fun propertyLookupParams(tenantId: UUID, field: QueryField.Property): List<Any?> =
    listOf(tenantId) + listOfNotNull(field.apiId, field.code)

  private fun lookupName(sql: String, params: List<Any?>): String {
    return jdbcTemplate
      .query(sql, params.toPreparedStatementSetter()) { rs, _ -> rs.getString(1) }
      .firstOrNull() ?: params.last().toString()
  }
}
