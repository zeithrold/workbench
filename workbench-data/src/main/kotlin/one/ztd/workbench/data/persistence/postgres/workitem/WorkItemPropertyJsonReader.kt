package one.ztd.workbench.data.persistence.postgres.workitem

import kotlin.uuid.toJavaUuid
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.data.persistence.postgres.identity.UsersTable
import one.ztd.workbench.data.persistence.postgres.project.ProjectsTable
import org.jetbrains.exposed.v1.core.ResultRow

private typealias PropertyJsonReader = (ResultRow) -> JsonElement?

private val propertyJsonReaders: List<PropertyJsonReader> =
  listOf(
    { row -> row[IssuePropertyValuesTable.valueText]?.let(::JsonPrimitive) },
    { row -> row[IssuePropertyValuesTable.valueNumber]?.let { JsonPrimitive(it) } },
    { row -> row[IssuePropertyValuesTable.valueBoolean]?.let { JsonPrimitive(it) } },
    { row -> row[IssuePropertyValuesTable.valueDate]?.let(::JsonPrimitive) },
    { row -> row[IssuePropertyValuesTable.valueDatetime]?.let { JsonPrimitive(it.toString()) } },
    { row -> row[IssuePropertyValuesTable.valueJson] },
    { row ->
      row[IssuePropertyValuesTable.valueUserId]?.let {
        JsonPrimitive(requirePublicId(UsersTable, it.toJavaUuid()).value)
      }
    },
    { row ->
      row[IssuePropertyValuesTable.valueProjectId]?.let {
        JsonPrimitive(requirePublicId(ProjectsTable, it.toJavaUuid()).value)
      }
    },
    { row ->
      row[IssuePropertyValuesTable.valueIssueId]?.let {
        JsonPrimitive(requirePublicId(IssuesTable, it.toJavaUuid()).value)
      }
    },
    { row ->
      row[IssuePropertyValuesTable.valueOptionId]?.let {
        JsonPrimitive(requirePublicId(PropertyOptionsTable, it.toJavaUuid()).value)
      }
    },
    { row -> row[IssuePropertyValuesTable.valueArray] },
  )

internal fun ResultRow.toJsonValue(): JsonElement =
  propertyJsonReaders.firstNotNullOfOrNull { it(this) } ?: JsonNull
