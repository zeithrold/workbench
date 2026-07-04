package ink.doa.workbench.core.workitem.template

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CreateFieldsPropertyMigrationRow(
  val code: String,
  val isRequired: Boolean,
  val defaultValue: JsonElement?,
)

object CreateFieldsLegacyMigrator {
  fun migrate(properties: List<CreateFieldsPropertyMigrationRow>): JsonObject {
    val fields = buildJsonObject {
      put(
        "title",
        buildJsonObject {
          put("participation", FieldParticipation.REQUIRED.wireName)
        },
      )
      put(
        "description",
        buildJsonObject {
          put("participation", FieldParticipation.OPTIONAL.wireName)
          put("writeGrant", FieldWriteGrant.INHERIT.wireName)
        },
      )
      properties.forEach { property ->
        val key = "property.${property.code}"
        val participation =
          when {
            property.defaultValue != null -> FieldParticipation.AUTOMATIC
            property.isRequired -> FieldParticipation.REQUIRED
            else -> FieldParticipation.OPTIONAL
          }
        put(
          key,
          buildJsonObject {
            put("participation", participation.wireName)
            property.defaultValue?.let { put("value", it) }
          },
        )
      }
    }

    return buildJsonObject {
      put("version", WorkItemTransitionFieldsTemplate.CURRENT_VERSION)
      put("resource", WorkItemTransitionFieldsTemplate.RESOURCE)
      put("target", WorkItemValueTemplateTarget.CREATE.wireName)
      put("fields", fields)
    }
  }
}
