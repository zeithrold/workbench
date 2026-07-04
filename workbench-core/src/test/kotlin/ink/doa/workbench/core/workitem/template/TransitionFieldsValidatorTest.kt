package ink.doa.workbench.core.workitem.template

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigPropertyRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigRecord
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class TransitionFieldsValidatorTest :
  StringSpec({
    val parser = TransitionFieldsParser()

    "validate accepts transition template with writable property" {
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
                "value": "fixed"
              }
            }
          }
          """
            .trimIndent()
        )

      TransitionFieldsValidator.validate(template, configWithProperty("resolution"))
    }

    "validate rejects unsupported version" {
      val template =
        WorkItemTransitionFieldsTemplate(
          version = 99,
          fields =
            mapOf(
              TemplateField.Property(apiId = null, code = "note") to
                TransitionFieldSpec(participation = FieldParticipation.OPTIONAL)
            ),
        )

      shouldThrow<InvalidRequestException> {
          TransitionFieldsValidator.validate(template, configWithProperty("note"))
        }
        .message shouldBe "Unsupported transition fields version: 99"
    }

    "validate rejects automatic field without value" {
      val template =
        WorkItemTransitionFieldsTemplate(
          fields =
            mapOf(
              TemplateField.System("assignee") to
                TransitionFieldSpec(participation = FieldParticipation.AUTOMATIC)
            ),
        )

      shouldThrow<InvalidRequestException> {
          TransitionFieldsValidator.validate(template, emptyConfig())
        }
        .message shouldBe "Automatic field requires a value expression: assignee"
    }

    "validateEnvelope rejects create target without fields" {
      val template =
        WorkItemTransitionFieldsTemplate(
          target = WorkItemValueTemplateTarget.CREATE,
          fields = emptyMap(),
        )

      shouldThrow<InvalidRequestException> {
          TransitionFieldsValidator.validateEnvelope(
            template,
            expectedTarget = WorkItemValueTemplateTarget.CREATE,
          )
        }
        .message shouldBe "Create fields template must define at least one field."
    }

    "validate rejects required comment without template" {
      val template =
        WorkItemTransitionFieldsTemplate(
          fields =
            mapOf(
              TemplateField.System("title") to
                TransitionFieldSpec(participation = FieldParticipation.OPTIONAL)
            ),
          comment =
            CommentFieldSpec(
              participation = FieldParticipation.REQUIRED,
              template = null,
            ),
        )

      shouldThrow<InvalidRequestException> {
          TransitionFieldsValidator.validate(template, emptyConfig())
        }
        .message shouldBe "Required transition comment must define a template."
    }

    "validate rejects automatic comment participation" {
      val template =
        WorkItemTransitionFieldsTemplate(
          fields =
            mapOf(
              TemplateField.System("title") to
                TransitionFieldSpec(participation = FieldParticipation.OPTIONAL)
            ),
          comment =
            CommentFieldSpec(
              participation = FieldParticipation.AUTOMATIC,
              template = TemplateValueExpression.Literal(JsonPrimitive("done")),
            ),
        )

      shouldThrow<InvalidRequestException> {
          TransitionFieldsValidator.validate(template, emptyConfig())
        }
        .message shouldBe "Comment participation cannot be automatic."
    }

    "validate rejects rich text description template literal" {
      val template =
        WorkItemTransitionFieldsTemplate(
          fields =
            mapOf(
              TemplateField.System("description") to
                TransitionFieldSpec(
                  participation = FieldParticipation.OPTIONAL,
                  value =
                    TemplateValueExpression.Literal(JsonPrimitive("<p>not plain</p>")),
                )
            ),
        )

      shouldThrow<InvalidRequestException> {
          TransitionFieldsValidator.validate(template, emptyConfig())
        }
        .message shouldBe "Description template must be plain text."
    }
  })

private fun emptyConfig(): IssueTypeConfigDetails = configWithProperty()

private fun configWithProperty(code: String = "resolution"): IssueTypeConfigDetails {
  val tenantId = UUID.randomUUID()
  val configId = UUID.randomUUID()
  val properties =
    if (code.isEmpty()) {
      emptyList()
    } else {
      listOf(
        IssueTypeConfigPropertyRecord(
          id = UUID.randomUUID(),
          tenantId = tenantId,
          issueTypeConfigId = configId,
          propertyId = UUID.randomUUID(),
          propertyApiId = PublicId.new("fld"),
          code = code,
          name = code,
          dataType = WorkItemPropertyDataType.TEXT,
          validationOverride = JsonObject(emptyMap()),
          rank = 100,
          displayConfig = JsonObject(emptyMap()),
        )
      )
    }
  return IssueTypeConfigDetails(
    config =
      IssueTypeConfigRecord(
        id = configId,
        apiId = PublicId.new("itc"),
        tenantId = tenantId,
        scope = WorkItemConfigScope.TENANT,
        projectId = null,
        issueTypeId = UUID.randomUUID(),
        issueTypeApiId = PublicId.new("typ"),
        workflowId = UUID.randomUUID(),
        workflowApiId = PublicId.new("wfl"),
        version = 1,
        nameOverride = null,
        iconOverride = null,
        colorOverride = null,
        rank = 100,
        isActive = true,
        validFrom = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        validTo = null,
        createdBy = null,
        createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        createFields = JsonObject(emptyMap()),
      ),
    statuses = emptyList(),
    properties = properties,
  )
}
