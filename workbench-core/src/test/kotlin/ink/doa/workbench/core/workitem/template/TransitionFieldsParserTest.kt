package ink.doa.workbench.core.workitem.template

import ink.doa.workbench.core.common.errors.InvalidRequestException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive

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
  })
