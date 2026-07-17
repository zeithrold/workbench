package one.ztd.workbench.agile.workitem.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.kernel.common.errors.InvalidRequestException

class TransitionFieldsParserTest :
  StringSpec({
    val parser = TransitionFieldsParser()

    "parses transition field specs with participation and write grant" {
      val template =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "target": "transition",
            "fields": {
              "property.resolution": {
                "participation": "required",
                "value": "fixed",
                "writeGrant": "transition_writable",
                "onUnauthorized": "reject"
              },
              "property.resolvedAt": {
                "participation": "optional",
                "value": { "var": "date.now" }
              },
              "assignee": {
                "participation": "automatic",
                "value": { "var": "user.currentUser" },
                "writeGrant": "system_only"
              }
            }
          }
          """
            .trimIndent()
        )

      template.fields.size shouldBe 3
      template.fields[TemplateField.Property(apiId = null, code = "resolution")] shouldBe
        TransitionFieldSpec(
          participation = FieldParticipation.REQUIRED,
          value = TemplateValueExpression.Literal(JsonPrimitive("fixed")),
          writeGrant = FieldWriteGrant.TRANSITION_WRITABLE,
          onUnauthorized = UnauthorizedMutationBehavior.REJECT,
        )
      template.fields[TemplateField.System("assignee")]?.participation shouldBe
        FieldParticipation.AUTOMATIC
    }

    "rejects invalid transition fields envelope" {
      shouldThrow<InvalidRequestException> {
          parser.parse(
            """{ "version": 2, "resource": "work_item", "target": "transition", "fields": {} }"""
          )
        }
        .message shouldBe "Unsupported transition fields version: 2"
    }

    "parses create fields with default value and comment spec" {
      val template =
        parser.parseCreateFields(
          kotlinx.serialization.json.Json.parseToJsonElement(
            """
            {
              "version": 1,
              "resource": "work_item",
              "target": "create",
              "fields": {
                "property.note": {
                  "participation": "optional",
                  "default": { "var": "date.today" },
                  "writeGrant": "inherit",
                  "onUnauthorized": "preserve_current"
                }
              },
              "comment": {
                "participation": "required",
                "template": "Created from template"
              }
            }
            """
              .trimIndent()
          )
        )

      template.target shouldBe WorkItemValueTemplateTarget.CREATE
      template.fields[TemplateField.Property(apiId = null, code = "note")] shouldBe
        TransitionFieldSpec(
          participation = FieldParticipation.OPTIONAL,
          value = TemplateValueExpression.Variable("date.today"),
        )
      template.comment shouldBe
        CommentFieldSpec(
          participation = FieldParticipation.REQUIRED,
          template = TemplateValueExpression.Literal(JsonPrimitive("Created from template")),
        )
    }

    "parses comment value fallback and default field participation" {
      val template =
        parser.parse(
          """
          {
            "version": 1,
            "resource": "work_item",
            "target": "transition",
            "fields": {
              "title": {
                "value": { "copy": "title" }
              }
            },
            "comment": {
              "value": { "var": "user.currentUser" }
            }
          }
          """
            .trimIndent()
        )

      template.fields[TemplateField.System("title")]?.participation shouldBe
        FieldParticipation.OPTIONAL
      template.comment?.template shouldBe TemplateValueExpression.Variable("user.currentUser")
    }

    "rejects create target when parsing transition fields" {
      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "target": "create",
              "fields": {}
            }
            """
              .trimIndent()
          )
        }
        .message shouldBe "Fields template target must be transition."
    }

    "rejects unknown target and invalid field enums" {
      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "target": "archive",
              "fields": {}
            }
            """
              .trimIndent()
          )
        }
        .message shouldBe "Unknown fields template target: archive"

      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "target": "transition",
              "fields": {
                "title": { "participation": "hidden", "value": "x" }
              }
            }
            """
              .trimIndent()
          )
        }
        .message shouldBe "Unknown field participation: hidden"

      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "target": "transition",
              "fields": {
                "title": { "writeGrant": "admin_only", "value": "x" }
              }
            }
            """
              .trimIndent()
          )
        }
        .message shouldBe "Unknown field write grant: admin_only"

      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "target": "transition",
              "fields": {
                "title": { "onUnauthorized": "ignore", "value": "x" }
              }
            }
            """
              .trimIndent()
          )
        }
        .message shouldBe "Unknown onUnauthorized behavior: ignore"
    }

    "rejects invalid json and missing required envelope fields" {
      shouldThrow<InvalidRequestException> { parser.parse("""{ "version": 1 """) }
        .message shouldStartWith
        "Invalid transition fields JSON: Unexpected JSON token at offset 15: Expected end of the object or comma at path: \$"

      shouldThrow<InvalidRequestException> {
          parser.parse("""{ "version": 1, "resource": "work_item", "target": "transition" }""")
        }
        .message shouldBe "Transition fields missing required field: fields"
    }

    "rejects create target when parsing create fields" {
      shouldThrow<InvalidRequestException> {
          parser.parseCreateFields(
            kotlinx.serialization.json.Json.parseToJsonElement(
              """
              {
                "version": 1,
                "resource": "work_item",
                "target": "transition",
                "fields": {}
              }
              """
            )
          )
        }
        .message shouldBe "Fields template target must be create."
    }

    "rejects invalid comment participation" {
      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "target": "transition",
              "fields": {},
              "comment": { "participation": "hidden" }
            }
            """
              .trimIndent()
          )
        }
        .message shouldBe "Unknown comment participation: hidden"
    }

    "rejects non-object field specs" {
      shouldThrow<InvalidRequestException> {
          parser.parse(
            """
            {
              "version": 1,
              "resource": "work_item",
              "target": "transition",
              "fields": { "title": "plain" }
            }
            """
              .trimIndent()
          )
        }
        .message shouldBe "Transition fields field spec must be an object."
    }
  })
