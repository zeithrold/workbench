package one.ztd.workbench.agile.workitem.template

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object TransitionFieldsLegacyMigrator {
  fun migrate(
    requiredProperties: JsonElement,
    optionalProperties: JsonElement,
    propertyDefaults: JsonObject,
  ): JsonObject {
    val requiredKeys = requiredProperties.propertyKeys().map { normalizeFieldKey(it) }.toSet()
    val optionalKeys = optionalProperties.propertyKeys().map { normalizeFieldKey(it) }.toSet()
    val defaultEntries =
      extractDefaultEntries(propertyDefaults).mapKeys { normalizeFieldKey(it.key) }
    val allKeys = requiredKeys + optionalKeys + defaultEntries.keys

    val fields = buildJsonObject {
      allKeys.forEach { key ->
        val participation =
          when {
            key in requiredKeys -> "required"
            key in optionalKeys -> "optional"
            else -> "automatic"
          }
        put(
          key,
          buildJsonObject {
            put("participation", participation)
            defaultEntries[key]?.let { put("value", it) }
          },
        )
      }
    }

    return buildJsonObject {
      put("version", WorkItemTransitionFieldsTemplate.CURRENT_VERSION)
      put("resource", WorkItemTransitionFieldsTemplate.RESOURCE)
      put("target", WorkItemValueTemplateTarget.TRANSITION.wireName)
      put("fields", fields)
    }
  }

  private fun extractDefaultEntries(propertyDefaults: JsonObject): Map<String, JsonElement> {
    if (propertyDefaults.isEmpty()) return emptyMap()
    return if ("version" in propertyDefaults && "values" in propertyDefaults) {
      checkNotNull(propertyDefaults["values"])
        .jsonObject
        .map { (key, value) -> key to value }
        .toMap()
    } else {
      propertyDefaults.toMap()
    }
  }

  private fun JsonElement.propertyKeys(): Set<String> =
    when (this) {
      is JsonArray -> mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
      is JsonObject -> keys
      else -> emptySet()
    }

  private fun normalizeFieldKey(key: String): String =
    when {
      key.startsWith("property.") -> key
      key in TemplateSystemFields.WRITABLE -> key
      else -> "property.$key"
    }
}
