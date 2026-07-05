package ink.doa.workbench.data.persistence.postgres.workitem.query

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.query.QueryField
import ink.doa.workbench.core.workitem.query.QueryOperator
import ink.doa.workbench.core.workitem.query.QueryValue
import ink.doa.workbench.core.workitem.query.WorkItemFieldDefinition
import ink.doa.workbench.core.workitem.query.WorkItemQueryFieldResolver
import ink.doa.workbench.core.workitem.query.WorkItemQueryFieldType

interface PostgresWorkItemFieldResolver : WorkItemQueryFieldResolver {
  fun resolvePostgresField(field: QueryField): PostgresWorkItemField

  override fun resolve(field: QueryField): WorkItemFieldDefinition =
    resolvePostgresField(field).definition
}

class StaticPostgresWorkItemFieldResolver(
  private val propertyTypes: Map<String, WorkItemQueryFieldType> = emptyMap()
) : PostgresWorkItemFieldResolver {
  override fun resolvePostgresField(field: QueryField): PostgresWorkItemField =
    when (field) {
      is QueryField.System -> systemField(field)
      is QueryField.Property -> propertyField(field, propertyTypes[field.apiId ?: field.code])
    }

  private fun systemField(field: QueryField.System): PostgresWorkItemField.System =
    SYSTEM_FIELDS[field.canonicalName]
      ?: throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_FIELD_UNKNOWN,
        "Unknown work item query field: ${field.canonicalName}",
      )

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
      definition =
        WorkItemFieldDefinition(
          field,
          resolvedType,
          sortable = resolvedType in SORTABLE_PROPERTY_TYPES,
        ),
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
        "project" to
          system("project", WorkItemQueryFieldType.PROJECT, "p.api_id", sortable = false),
        "issueType" to
          system(
            "issueType",
            WorkItemQueryFieldType.SINGLE_SELECT,
            "itype.api_id",
            sortable = true,
          ),
        "title" to system("title", WorkItemQueryFieldType.TEXT, "i.title", sortable = true),
        "description" to
          system(
            "description",
            WorkItemQueryFieldType.TEXT,
            "i.description_plain_text",
            sortable = false,
          ),
        "status" to
          system("status", WorkItemQueryFieldType.SINGLE_SELECT, "st.api_id", sortable = true),
        "statusGroup" to
          system(
            "statusGroup",
            WorkItemQueryFieldType.SINGLE_SELECT,
            "st.status_group",
            sortable = true,
          ),
        "priority" to
          system("priority", WorkItemQueryFieldType.SINGLE_SELECT, "pri.api_id", sortable = true),
        "reporter" to
          system("reporter", WorkItemQueryFieldType.USER, "reporter.api_id", sortable = true),
        "assignee" to
          system("assignee", WorkItemQueryFieldType.USER, "assignee.api_id", sortable = true),
        "sprint" to system("sprint", WorkItemQueryFieldType.ID, "sprint.api_id", sortable = true),
        "createdBy" to
          system(
            "createdBy",
            WorkItemQueryFieldType.USER,
            "created_by_user.api_id",
            sortable = false,
          ),
        "updatedBy" to
          system(
            "updatedBy",
            WorkItemQueryFieldType.USER,
            "updated_by_user.api_id",
            sortable = false,
          ),
        "createdAt" to
          system("createdAt", WorkItemQueryFieldType.DATETIME, "i.created_at", sortable = true),
        "updatedAt" to
          system("updatedAt", WorkItemQueryFieldType.DATETIME, "i.updated_at", sortable = true),
        "archivedAt" to
          system("archivedAt", WorkItemQueryFieldType.DATETIME, "i.archived_at", sortable = false),
        "deletedAt" to
          system("deletedAt", WorkItemQueryFieldType.DATETIME, "i.deleted_at", sortable = false),
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
        "children.issueType" to
          system(
            "children.issueType",
            WorkItemQueryFieldType.SINGLE_SELECT,
            "NULL",
            sortable = false,
            predicateCompiler = ::childrenIssueTypePredicate,
          ),
      )

    private fun system(
      name: String,
      type: WorkItemQueryFieldType,
      valueSql: String,
      sortable: Boolean,
      predicateCompiler: ((QueryOperator, QueryValue?) -> SqlFragment)? = null,
    ) =
      PostgresWorkItemField.System(
        definition = WorkItemFieldDefinition(QueryField.System(name), type, sortable),
        valueSql = valueSql,
        predicateCompiler = predicateCompiler,
      )

    private fun childrenIssueTypePredicate(op: QueryOperator, value: QueryValue?): SqlFragment =
      when (op) {
        QueryOperator.EQ -> childrenIssueTypeExists("=", listOf(value.requireStringLiteral()))
        QueryOperator.NEQ ->
          childrenIssueTypeExists("=", listOf(value.requireStringLiteral()), negated = true)
        QueryOperator.IN ->
          childrenIssueTypeExists("IN", value.asLiteralArray().map(::jsonToJdbcValue))
        QueryOperator.NOT_IN ->
          childrenIssueTypeExists(
            "IN",
            value.asLiteralArray().map(::jsonToJdbcValue),
            negated = true,
          )
        QueryOperator.IS_EMPTY ->
          SqlFragment(
            """
            NOT EXISTS (
              SELECT 1
              FROM issue_hierarchy child_ih
              WHERE child_ih.parent_issue_id = i.id
                AND child_ih.tenant_id = i.tenant_id
            )
            """
              .trimIndent()
          )
        QueryOperator.IS_NOT_EMPTY ->
          SqlFragment(
            """
            EXISTS (
              SELECT 1
              FROM issue_hierarchy child_ih
              WHERE child_ih.parent_issue_id = i.id
                AND child_ih.tenant_id = i.tenant_id
            )
            """
              .trimIndent()
          )
        else -> error("Unsupported children.issueType operator: $op")
      }

    private fun childrenIssueTypeExists(
      operator: String,
      values: List<Any?>,
      negated: Boolean = false,
    ): SqlFragment {
      val predicate =
        if (operator == "IN" || operator == "NOT IN") {
          val placeholders = values.joinToString(", ") { "?" }
          "child_type.api_id $operator ($placeholders)"
        } else {
          "child_type.api_id $operator ?"
        }
      val existsSql =
        """
        EXISTS (
          SELECT 1
          FROM issue_hierarchy child_ih
          JOIN issues child_issue ON child_issue.id = child_ih.child_issue_id
          JOIN issue_types child_type ON child_type.id = child_issue.issue_type_id
          WHERE child_ih.parent_issue_id = i.id
            AND child_ih.tenant_id = i.tenant_id
            AND child_issue.archived_at IS NULL
            AND child_issue.deleted_at IS NULL
            AND $predicate
        )
        """
          .trimIndent()
      return SqlFragment(if (negated) "NOT ($existsSql)" else existsSql, values)
    }

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
