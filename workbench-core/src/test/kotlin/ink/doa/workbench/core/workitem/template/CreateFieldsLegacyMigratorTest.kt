package ink.doa.workbench.core.workitem.template

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CreateFieldsLegacyMigratorTest :
  StringSpec({
    "maps required optional and automatic property bindings" {
      val migrated =
        CreateFieldsLegacyMigrator.migrate(
          listOf(
            CreateFieldsPropertyMigrationRow(
              code = "summary",
              isRequired = true,
              defaultValue = null,
            ),
            CreateFieldsPropertyMigrationRow(
              code = "dueDate",
              isRequired = false,
              defaultValue =
                Json.parseToJsonElement(
                  """{"relativeDate":{"amount":3,"unit":"day","direction":"future","anchor":"date.today"}}"""
                ),
            ),
            CreateFieldsPropertyMigrationRow(
              code = "labels",
              isRequired = false,
              defaultValue = null,
            ),
          )
        )

      migrated["target"]!!.jsonPrimitive.content shouldBe
        WorkItemValueTemplateTarget.CREATE.wireName
      migrated["fields"]!!
        .jsonObject["title"]!!
        .jsonObject["participation"]!!
        .jsonPrimitive
        .content shouldBe "required"
      migrated["fields"]!!
        .jsonObject["property.summary"]!!
        .jsonObject["participation"]!!
        .jsonPrimitive
        .content shouldBe "required"
      migrated["fields"]!!
        .jsonObject["property.dueDate"]!!
        .jsonObject["participation"]!!
        .jsonPrimitive
        .content shouldBe "automatic"
      migrated["fields"]!!
        .jsonObject["property.labels"]!!
        .jsonObject["participation"]!!
        .jsonPrimitive
        .content shouldBe "optional"
    }
  })
