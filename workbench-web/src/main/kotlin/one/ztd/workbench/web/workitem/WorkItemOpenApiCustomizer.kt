package one.ztd.workbench.web.workitem

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Schema
import org.springdoc.core.customizers.OpenApiCustomizer

/**
 * Springdoc currently drops nullable metadata from Kotlin properties that resolve directly to a
 * component reference. Normalize the generated component schemas so TypeScript clients preserve the
 * runtime null contract instead of treating these fields as merely absent.
 */
class WorkItemOpenApiCustomizer : OpenApiCustomizer {
  override fun customise(openApi: OpenAPI) {
    val nullableProperties =
      mapOf(
        "WorkItemResponse" to
          setOf("description", "priority", "assignee", "sprint", "groupKey", "groupLabel"),
        "WorkItemIssueTypeSummaryResponse" to setOf("icon", "color"),
        "WorkItemStatusSummaryResponse" to setOf("color"),
        "WorkItemPrioritySummaryResponse" to setOf("icon", "color"),
        "WorkItemSprintSummaryResponse" to setOf("startAt", "endAt"),
        "WorkItemSearchGroupsPageInfoResponse" to setOf("nextGroupCursor"),
      )

    nullableProperties.forEach { (schemaName, properties) ->
      val schema = openApi.components?.schemas?.get(schemaName) ?: return@forEach
      properties.forEach { property ->
        val propertySchema = schema.properties?.get(property) ?: return@forEach
        val nullSchema = Schema<Any>().types(setOf("null"))
        schema.properties[property] = ComposedSchema().anyOf(listOf(propertySchema, nullSchema))
      }
    }
  }
}
