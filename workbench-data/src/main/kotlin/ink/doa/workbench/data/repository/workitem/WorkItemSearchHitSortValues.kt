package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.core.workitem.model.WorkItemSearchHit
import ink.doa.workbench.core.workitem.query.QueryField
import ink.doa.workbench.core.workitem.query.WorkItemGroupKeySupport
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

internal object WorkItemSearchHitSortValues {
  fun valueForField(field: QueryField, hit: WorkItemSearchHit): JsonElement =
    when (field.canonicalName) {
      in NON_NULL_STRING_FIELDS -> JsonPrimitive(nonNullStringValue(field.canonicalName, hit))
      in NULLABLE_STRING_FIELDS ->
        nullableStringValue(field.canonicalName, hit)?.let(::JsonPrimitive) ?: JsonNull
      else -> JsonNull
    }

  fun bucketValueForField(field: QueryField, hit: WorkItemSearchHit): Any? =
    when (val element = valueForField(field, hit)) {
      is JsonNull -> null
      is JsonPrimitive ->
        WorkItemGroupKeySupport.bucketValueFromKey(
          ink.doa.workbench.core.workitem.query.ConditionNode.Predicate(
            field = field,
            op = ink.doa.workbench.core.workitem.query.QueryOperator.EQ,
            value = ink.doa.workbench.core.workitem.query.QueryValue.Literal(element),
          )
        )
      else -> null
    }

  private val NON_NULL_STRING_FIELDS =
    setOf(
      "apiId",
      "key",
      "title",
      "status",
      "statusGroup",
      "issueType",
      "reporter",
      "createdAt",
      "updatedAt",
    )

  private val NULLABLE_STRING_FIELDS = setOf("priority", "assignee", "sprint")

  private fun nonNullStringValue(name: String, hit: WorkItemSearchHit): String =
    when (name) {
      "apiId" -> hit.apiId
      "key" -> hit.key
      "title" -> hit.title
      "status" -> hit.statusApiId
      "statusGroup" -> hit.statusGroup
      "issueType" -> hit.issueTypeApiId
      "reporter" -> hit.reporterApiId
      "createdAt" -> hit.createdAt.toString()
      "updatedAt" -> hit.updatedAt.toString()
      else -> error("Unsupported non-null sort field: $name")
    }

  private fun nullableStringValue(name: String, hit: WorkItemSearchHit): String? =
    when (name) {
      "priority" -> hit.priorityApiId
      "assignee" -> hit.assigneeApiId
      "sprint" -> hit.sprintApiId
      else -> error("Unsupported nullable sort field: $name")
    }
}
