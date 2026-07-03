package doa.ink.workbench.core.workitem.query

interface WorkItemQueryFieldResolver {
  fun resolve(field: QueryField): WorkItemFieldDefinition
}

data class WorkItemFieldDefinition(
  val field: QueryField,
  val type: WorkItemQueryFieldType,
  val sortable: Boolean,
)

enum class WorkItemQueryFieldType(val supportedOperators: Set<QueryOperator>) {
  ID(REFERENCE_OPERATORS),
  TEXT(TEXT_OPERATORS),
  LONG_TEXT(LONG_TEXT_OPERATORS),
  NUMBER(NUMBER_OPERATORS),
  BOOLEAN(BOOLEAN_OPERATORS),
  DATE(DATE_OPERATORS),
  DATETIME(DATE_OPERATORS),
  SINGLE_SELECT(REFERENCE_OPERATORS),
  MULTI_SELECT(ARRAY_OPERATORS),
  USER(REFERENCE_OPERATORS),
  MULTI_USER(ARRAY_OPERATORS),
  PROJECT(REFERENCE_OPERATORS),
  ISSUE(REFERENCE_OPERATORS),
  JSON(JSON_OPERATORS),
}
