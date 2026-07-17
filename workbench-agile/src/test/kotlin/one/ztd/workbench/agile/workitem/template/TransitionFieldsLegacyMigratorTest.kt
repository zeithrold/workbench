package one.ztd.workbench.agile.workitem.template

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class TransitionFieldsLegacyMigratorTest :
  StringSpec({
    val json = Json { ignoreUnknownKeys = true }

    "merges required optional and envelope property defaults" {
      val migrated =
        TransitionFieldsLegacyMigrator.migrate(
          requiredProperties = json.parseToJsonElement("""["resolution"]"""),
          optionalProperties = json.parseToJsonElement("""[]"""),
          propertyDefaults =
            json
              .parseToJsonElement(
                """
                {
                  "version": 1,
                  "resource": "work_item",
                  "target": "transition",
                  "values": {
                    "assignee": { "var": "user.currentUser" },
                    "property.resolution": "fixed",
                    "property.resolvedAt": { "var": "date.now" }
                  }
                }
                """
                  .trimIndent()
              )
              .jsonObject,
        )

      val fields = migrated["fields"]!!.jsonObject
      fields["property.resolution"]!!.jsonObject["participation"]!!.toString() shouldBe
        "\"required\""
      fields["property.resolvedAt"]!!.jsonObject["participation"]!!.toString() shouldBe
        "\"automatic\""
      fields["assignee"]!!.jsonObject["participation"]!!.toString() shouldBe "\"automatic\""
    }

    "merges flat property defaults map" {
      val migrated =
        TransitionFieldsLegacyMigrator.migrate(
          requiredProperties = json.parseToJsonElement("""[]"""),
          optionalProperties = json.parseToJsonElement("""["note"]"""),
          propertyDefaults =
            JsonObject(mapOf("property.note" to json.parseToJsonElement("\"hello\""))),
        )

      migrated["fields"]!!
        .jsonObject["property.note"]!!
        .jsonObject["participation"]!!
        .toString() shouldBe "\"optional\""
    }
  })
