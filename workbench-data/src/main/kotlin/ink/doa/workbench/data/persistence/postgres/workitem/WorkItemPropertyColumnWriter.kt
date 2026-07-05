package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.data.persistence.IssuePropertyValuesTable
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

private typealias PropertyColumnWriter = (UpdateBuilder<*>, UUID, WorkItemPropertyValue) -> Unit

private val textColumnWriter: PropertyColumnWriter = { row, _, value ->
  row[IssuePropertyValuesTable.valueText] = (value.value as? JsonPrimitive)?.content
}

private val numberColumnWriter: PropertyColumnWriter = { row, _, value ->
  row[IssuePropertyValuesTable.valueNumber] =
    (value.value as? JsonPrimitive)?.content?.let(::BigDecimal)
}

private val booleanColumnWriter: PropertyColumnWriter = { row, _, value ->
  row[IssuePropertyValuesTable.valueBoolean] =
    (value.value as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
}

private val dateColumnWriter: PropertyColumnWriter = { row, _, value ->
  row[IssuePropertyValuesTable.valueDate] = (value.value as? JsonPrimitive)?.content
}

private val datetimeColumnWriter: PropertyColumnWriter = { row, _, value ->
  row[IssuePropertyValuesTable.valueDatetime] =
    (value.value as? JsonPrimitive)?.content?.let(OffsetDateTime::parse)
}

private val jsonColumnWriter: PropertyColumnWriter = { row, _, value ->
  row[IssuePropertyValuesTable.valueJson] = value.value
}

private val userColumnWriter: PropertyColumnWriter = { row, _, value ->
  row[IssuePropertyValuesTable.valueUserId] =
    (value.value as? JsonPrimitive)?.content?.let(::resolveUser)?.toKotlinUuid()
}

private val projectColumnWriter: PropertyColumnWriter = { row, tenantId, value ->
  row[IssuePropertyValuesTable.valueProjectId] =
    (value.value as? JsonPrimitive)?.content?.let { resolveProject(tenantId, it) }?.toKotlinUuid()
}

private val issueColumnWriter: PropertyColumnWriter = { row, tenantId, value ->
  row[IssuePropertyValuesTable.valueIssueId] =
    (value.value as? JsonPrimitive)?.content?.let { resolveIssue(tenantId, it) }?.toKotlinUuid()
}

private val singleSelectColumnWriter: PropertyColumnWriter = { row, _, value ->
  row[IssuePropertyValuesTable.valueOptionId] =
    (value.value as? JsonPrimitive)
      ?.content
      ?.let { resolveOption(value.propertyId, it) }
      ?.toKotlinUuid()
}

private val arrayColumnWriter: PropertyColumnWriter = { row, _, value ->
  row[IssuePropertyValuesTable.valueArray] = value.value
}

private val propertyColumnWriters: Map<WorkItemPropertyDataType, PropertyColumnWriter> =
  mapOf(
    WorkItemPropertyDataType.TEXT to textColumnWriter,
    WorkItemPropertyDataType.LONG_TEXT to textColumnWriter,
    WorkItemPropertyDataType.URL to textColumnWriter,
    WorkItemPropertyDataType.NUMBER to numberColumnWriter,
    WorkItemPropertyDataType.BOOLEAN to booleanColumnWriter,
    WorkItemPropertyDataType.DATE to dateColumnWriter,
    WorkItemPropertyDataType.DATETIME to datetimeColumnWriter,
    WorkItemPropertyDataType.JSON to jsonColumnWriter,
    WorkItemPropertyDataType.USER to userColumnWriter,
    WorkItemPropertyDataType.PROJECT to projectColumnWriter,
    WorkItemPropertyDataType.ISSUE to issueColumnWriter,
    WorkItemPropertyDataType.SINGLE_SELECT to singleSelectColumnWriter,
    WorkItemPropertyDataType.MULTI_SELECT to arrayColumnWriter,
    WorkItemPropertyDataType.MULTI_USER to arrayColumnWriter,
  )

internal fun writePropertyColumns(
  row: UpdateBuilder<*>,
  tenantId: UUID,
  value: WorkItemPropertyValue,
) {
  propertyColumnWriters.getValue(value.dataType)(row, tenantId, value)
}
