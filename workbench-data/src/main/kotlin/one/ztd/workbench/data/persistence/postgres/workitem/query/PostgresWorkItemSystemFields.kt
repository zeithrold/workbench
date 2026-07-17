package one.ztd.workbench.data.persistence.postgres.workitem.query

import one.ztd.workbench.agile.workitem.query.QueryField
import one.ztd.workbench.agile.workitem.query.QueryOperator
import one.ztd.workbench.agile.workitem.query.QueryValue
import one.ztd.workbench.agile.workitem.query.WorkItemFieldDefinition
import one.ztd.workbench.agile.workitem.query.WorkItemQueryFieldResolver
import one.ztd.workbench.agile.workitem.query.WorkItemQueryFieldType
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode

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
          groupable = resolvedType in GROUPABLE_PROPERTY_TYPES,
        ),
      valueSql = propertyValueColumn(resolvedType),
      identitySql = identityParts.joinToString(" AND "),
      identityParams = identityParams,
      groupSql =
        propertyGroupSql(identityParts.joinToString(" AND "), propertyValueColumn(resolvedType)),
    )
  }

  companion object {
    private data class SystemFieldSpec(
      val name: String,
      val type: WorkItemQueryFieldType,
      val valueSql: String,
      val sortable: Boolean,
      val groupable: Boolean = false,
      val predicateCompiler: ((QueryOperator, QueryValue?) -> SqlFragment)? = null,
    )

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

    private val GROUPABLE_PROPERTY_TYPES =
      setOf(WorkItemQueryFieldType.SINGLE_SELECT, WorkItemQueryFieldType.USER)

    private val SYSTEM_FIELDS =
      mapOf(
        "id" to system(SystemFieldSpec("id", WorkItemQueryFieldType.ID, "i.id", sortable = false)),
        "apiId" to
          system(
            SystemFieldSpec("apiId", WorkItemQueryFieldType.TEXT, "i.api_id", sortable = false)
          ),
        "key" to
          system(
            SystemFieldSpec(
              "key",
              WorkItemQueryFieldType.TEXT,
              "COALESCE(keya.issue_key, p.identifier || '-' || i.sequence_no)",
              sortable = true,
            )
          ),
        "tenant" to
          system(
            SystemFieldSpec("tenant", WorkItemQueryFieldType.ID, "i.tenant_id", sortable = false)
          ),
        "project" to
          system(
            SystemFieldSpec("project", WorkItemQueryFieldType.PROJECT, "p.api_id", sortable = false)
          ),
        "issueType" to
          system(
            SystemFieldSpec(
              "issueType",
              WorkItemQueryFieldType.SINGLE_SELECT,
              "itype.api_id",
              sortable = true,
              groupable = true,
            )
          ),
        "title" to
          system(SystemFieldSpec("title", WorkItemQueryFieldType.TEXT, "i.title", sortable = true)),
        "description" to
          system(
            SystemFieldSpec(
              "description",
              WorkItemQueryFieldType.TEXT,
              "i.description_plain_text",
              sortable = false,
            )
          ),
        "status" to
          system(
            SystemFieldSpec(
              "status",
              WorkItemQueryFieldType.SINGLE_SELECT,
              "st.api_id",
              sortable = true,
              groupable = true,
            )
          ),
        "statusGroup" to
          system(
            SystemFieldSpec(
              "statusGroup",
              WorkItemQueryFieldType.SINGLE_SELECT,
              "st.status_group",
              sortable = true,
              groupable = true,
            )
          ),
        "priority" to
          system(
            SystemFieldSpec(
              "priority",
              WorkItemQueryFieldType.SINGLE_SELECT,
              "pri.api_id",
              sortable = true,
              groupable = true,
            )
          ),
        "reporter" to
          system(
            SystemFieldSpec(
              "reporter",
              WorkItemQueryFieldType.USER,
              "reporter.api_id",
              sortable = true,
              groupable = true,
            )
          ),
        "assignee" to
          system(
            SystemFieldSpec(
              "assignee",
              WorkItemQueryFieldType.USER,
              "assignee.api_id",
              sortable = true,
              groupable = true,
            )
          ),
        "sprint" to
          system(
            SystemFieldSpec(
              "sprint",
              WorkItemQueryFieldType.ID,
              "sprint.api_id",
              sortable = true,
              groupable = true,
            )
          ),
        "createdBy" to
          system(
            SystemFieldSpec(
              "createdBy",
              WorkItemQueryFieldType.USER,
              "created_by_user.api_id",
              sortable = false,
            )
          ),
        "updatedBy" to
          system(
            SystemFieldSpec(
              "updatedBy",
              WorkItemQueryFieldType.USER,
              "updated_by_user.api_id",
              sortable = false,
            )
          ),
        "createdAt" to
          system(
            SystemFieldSpec(
              "createdAt",
              WorkItemQueryFieldType.DATETIME,
              "i.created_at",
              sortable = true,
            )
          ),
        "updatedAt" to
          system(
            SystemFieldSpec(
              "updatedAt",
              WorkItemQueryFieldType.DATETIME,
              "i.updated_at",
              sortable = true,
            )
          ),
        "archivedAt" to
          system(
            SystemFieldSpec(
              "archivedAt",
              WorkItemQueryFieldType.DATETIME,
              "i.archived_at",
              sortable = false,
            )
          ),
        "deletedAt" to
          system(
            SystemFieldSpec(
              "deletedAt",
              WorkItemQueryFieldType.DATETIME,
              "i.deleted_at",
              sortable = false,
            )
          ),
        "parent" to
          system(
            SystemFieldSpec(
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
            )
          ),
        "children.count" to
          system(
            SystemFieldSpec(
              "children.count",
              WorkItemQueryFieldType.NUMBER,
              "(SELECT COUNT(*) FROM issue_hierarchy child_ih WHERE child_ih.parent_issue_id = i.id)",
              sortable = false,
            )
          ),
        "children.issueType" to
          system(
            SystemFieldSpec(
              "children.issueType",
              WorkItemQueryFieldType.SINGLE_SELECT,
              "NULL",
              sortable = false,
              predicateCompiler = ::childrenIssueTypePredicate,
            )
          ),
      )

    private fun system(spec: SystemFieldSpec) =
      PostgresWorkItemField.System(
        definition =
          WorkItemFieldDefinition(
            QueryField.System(spec.name),
            spec.type,
            sortable = spec.sortable,
            groupable = spec.groupable,
          ),
        valueSql = spec.valueSql,
        predicateCompiler = spec.predicateCompiler,
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

    private fun propertyGroupSql(identitySql: String, valueSql: String): String =
      """
      (
        SELECT $valueSql
        FROM issue_property_values ipv
        JOIN property_definitions pd ON pd.id = ipv.property_id
        LEFT JOIN property_options option_value ON option_value.id = ipv.value_option_id
        LEFT JOIN users user_value ON user_value.id = ipv.value_user_id
        LEFT JOIN projects project_value ON project_value.id = ipv.value_project_id
        LEFT JOIN issues issue_value ON issue_value.id = ipv.value_issue_id
        WHERE ipv.issue_id = i.id
          AND ipv.tenant_id = i.tenant_id
          AND $identitySql
        LIMIT 1
      )
      """
        .trimIndent()
  }
}
