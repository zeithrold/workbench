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
  })
