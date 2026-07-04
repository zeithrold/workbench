package ink.doa.workbench.core.workitem.template

import ink.doa.workbench.core.common.errors.InvalidRequestException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive

class WorkItemValueTemplateParserTest :
  StringSpec({
    val parser = WorkItemValueTemplateParser()

    "parses canonical template expressions" {
      val template =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "target": "transition",
            "values": {
              "assignee": { "var": "user.currentUser" },
              "property.dueDate": {
                "relativeDate": {
                  "amount": 3,
                  "unit": "day",
                  "direction": "future",
                  "anchor": "date.today"
                }
              },
              "property.copyOfResolution": { "copy": "property.resolution" },
              "property.optionalText": { "clear": true },
              "property.resolution": "fixed"
            }
          }
          """
            .trimIndent()
        )

      template.target shouldBe WorkItemValueTemplateTarget.TRANSITION
      template.values[TemplateField.System("assignee")] shouldBe
        TemplateValueExpression.Variable("user.currentUser")
      template.values[TemplateField.Property(apiId = null, code = "resolution")] shouldBe
        TemplateValueExpression.Literal(JsonPrimitive("fixed"))
      template.values[TemplateField.Property(apiId = null, code = "optionalText")] shouldBe
        TemplateValueExpression.Clear
    }

    "rejects invalid envelopes" {
      shouldThrow<InvalidRequestException> {
          parser.parse(
            """{ "version": 2, "resource": "work_item", "target": "create", "values": {} }"""
          )
        }
        .message shouldBe "Unsupported work item value template version: 2"
    }

    "parses create target templates" {
      val template =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "target": "create",
            "values": {
              "title": { "var": "user.currentUser" }
            }
          }
          """
            .trimIndent()
        )
      template.target shouldBe WorkItemValueTemplateTarget.CREATE
    }

    "rejects unsupported resource" {
      shouldThrow<InvalidRequestException> {
        parser.parse(
          """{ "version": 1, "resource": "project", "target": "create", "values": {} }"""
        )
      }
    }

    "parses property api id field paths" {
      val template =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "target": "create",
            "values": {
              "property.fld_123": "ready"
            }
          }
          """
            .trimIndent()
        )

      template.values[TemplateField.Property(apiId = "fld_123", code = null)] shouldBe
        TemplateValueExpression.Literal(JsonPrimitive("ready"))
    }

    "parses relativeDateTime expressions" {
      val expression =
        parser.parseExpression(
          kotlinx.serialization.json.Json.parseToJsonElement(
            """
              {
                "relativeDateTime": {
                  "amount": 1,
                  "unit": "week",
                  "direction": "past",
                  "anchor": "date.today"
                }
              }
              """
          )
        )

      expression shouldBe
        TemplateValueExpression.RelativeDate(
          amount = 1,
          unit = TemplateRelativeDateUnit.WEEK,
          direction = TemplateDateDirection.PAST,
          anchor = "date.today",
        )
    }

    "rejects invalid json payloads" {
      shouldThrow<InvalidRequestException> {
        parser.parse("""{ "version": 1, "resource": """)
      }
    }

    "rejects unknown template targets" {
      shouldThrow<InvalidRequestException> {
        parser.parse(
          """{ "version": 1, "resource": "work_item", "target": "archive", "values": {} }"""
        )
      }
    }

    "rejects blank property identities" {
      shouldThrow<InvalidRequestException> {
        parser.parseFieldPath("property.")
      }
    }

    "rejects templates missing required fields" {
      shouldThrow<InvalidRequestException> {
        parser.parse("""{ "version": 1, "resource": "work_item", "target": "create" }""")
      }
    }

    "rejects non-object template payloads" {
      shouldThrow<InvalidRequestException> {
        parser.parse("""["version", 1]""")
      }
    }

    "rejects non-string variable references" {
      shouldThrow<InvalidRequestException> {
        parser.parseExpression(
          kotlinx.serialization.json.Json.parseToJsonElement("""{ "var": {} }""")
        )
      }
    }

    "rejects unknown relative date units" {
      shouldThrow<InvalidRequestException> {
        parser.parseExpression(
          kotlinx.serialization.json.Json.parseToJsonElement(
            """
            {
              "relativeDate": {
                "amount": 1,
                "unit": "fortnight",
                "direction": "future",
                "anchor": "date.today"
              }
            }
            """
          )
        )
      }
    }

    "rejects non-integer template versions" {
      shouldThrow<InvalidRequestException> {
        parser.parse(
          """{ "version": "one", "resource": "work_item", "target": "create", "values": {} }"""
        )
      }
    }

    "rejects unknown relative date directions" {
      shouldThrow<InvalidRequestException> {
        parser.parseExpression(
          kotlinx.serialization.json.Json.parseToJsonElement(
            """
            {
              "relativeDate": {
                "amount": 1,
                "unit": "day",
                "direction": "sideways",
                "anchor": "date.today"
              }
            }
            """
          )
        )
      }
    }

    "parses object literal expressions" {
      val expression =
        parser.parseExpression(
          kotlinx.serialization.json.Json.parseToJsonElement("""{"label": "Ready"}""")
        )

      expression shouldBe
        TemplateValueExpression.Literal(
          kotlinx.serialization.json.Json.parseToJsonElement("""{"label": "Ready"}""")
        )
    }

    "ignores clear flag when not true" {
      val expression =
        parser.parseExpression(
          kotlinx.serialization.json.Json.parseToJsonElement("""{"clear": false}""")
        )

      expression shouldBe
        TemplateValueExpression.Literal(
          kotlinx.serialization.json.JsonObject(mapOf("clear" to JsonPrimitive(false)))
        )
    }
  })
