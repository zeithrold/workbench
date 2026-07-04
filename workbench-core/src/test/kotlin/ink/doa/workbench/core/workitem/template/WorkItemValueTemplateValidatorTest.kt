package ink.doa.workbench.core.workitem.template

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
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

class WorkItemValueTemplateValidatorTest :
  StringSpec({
    val config = sampleConfig()

    "validateEnvelope rejects unsupported version" {
      val template =
        WorkItemValueTemplate(
          version = 99,
          target = WorkItemValueTemplateTarget.CREATE,
          values = emptyMap(),
        )
      shouldThrow<InvalidRequestException> {
          WorkItemValueTemplateValidator.validateEnvelope(template)
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_TEMPLATE_VERSION_UNSUPPORTED
    }

    "validateEnvelope rejects unsupported resource" {
      val template =
        WorkItemValueTemplate(
          resource = "project",
          target = WorkItemValueTemplateTarget.CREATE,
          values = emptyMap(),
        )
      shouldThrow<InvalidRequestException> {
          WorkItemValueTemplateValidator.validateEnvelope(template)
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_TEMPLATE_RESOURCE_UNSUPPORTED
    }

    "validateEnvelope rejects too many values" {
      val writableFields = listOf("title", "description", "assignee", "priority", "sprint")
      val values: Map<TemplateField, TemplateValueExpression> =
        (1..65).associate { index ->
          val fieldName = writableFields[(index - 1) % writableFields.size]
          TemplateField.System("$fieldName$index") to
            TemplateValueExpression.Variable("user.currentUser")
        }
      val template =
        WorkItemValueTemplate(
          target = WorkItemValueTemplateTarget.CREATE,
          values = values,
        )
      shouldThrow<InvalidRequestException> {
          WorkItemValueTemplateValidator.validateEnvelope(template)
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_TEMPLATE_TOO_MANY_VALUES
    }

    "validateWritableField rejects non-writable system field" {
      shouldThrow<InvalidRequestException> {
          WorkItemValueTemplateValidator.validateWritableField(
            TemplateField.System("status"),
            config,
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_TEMPLATE_FIELD_NOT_WRITABLE
    }

    "validateWritableField accepts writable system field" {
      WorkItemValueTemplateValidator.validateWritableField(
        TemplateField.System("title"),
        config,
      ) shouldBe null
    }

    "resolveProperty matches by code" {
      val property = config.properties.single { it.code == "dueDate" }
      WorkItemValueTemplateValidator.resolveProperty(
        TemplateField.Property(apiId = null, code = "dueDate"),
        config,
      ) shouldBe property
    }

    "resolveProperty throws when property unavailable" {
      shouldThrow<InvalidRequestException> {
          WorkItemValueTemplateValidator.resolveProperty(
            TemplateField.Property(apiId = null, code = "missing"),
            config,
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_PROPERTY_UNAVAILABLE
    }

    "validateExpression accepts known variables" {
      WorkItemValueTemplateValidator.validateExpression(
        TemplateValueExpression.Variable("user.currentUser"),
        config,
        null,
      )
      WorkItemValueTemplateValidator.validateExpression(
        TemplateValueExpression.Variable("workItem.current.title"),
        config,
        null,
      )
    }

    "validateExpression rejects unknown variable" {
      shouldThrow<InvalidRequestException> {
          WorkItemValueTemplateValidator.validateExpression(
            TemplateValueExpression.Variable("user.unknown"),
            config,
            null,
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_TEMPLATE_VARIABLE_UNKNOWN
    }

    "validateExpression validates literal against property type" {
      val property = config.properties.single { it.code == "dueDate" }
      shouldThrow<InvalidRequestException> {
          WorkItemValueTemplateValidator.validateExpression(
            TemplateValueExpression.Literal(JsonPrimitive("not-a-date")),
            config,
            property,
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_PROPERTY_VALUE_INVALID
    }

    "validateExpression accepts relative date on date property" {
      val property = config.properties.single { it.code == "dueDate" }
      WorkItemValueTemplateValidator.validateExpression(
        TemplateValueExpression.RelativeDate(
          amount = 2,
          unit = TemplateRelativeDateUnit.DAY,
          direction = TemplateDateDirection.FUTURE,
          anchor = "date.today",
        ),
        config,
        property,
      )
    }

    "validateExpression rejects relative date on non-date property" {
      val property = config.properties.single { it.code == "resolution" }
      shouldThrow<InvalidRequestException> {
          WorkItemValueTemplateValidator.validateExpression(
            TemplateValueExpression.RelativeDate(
              amount = 2,
              unit = TemplateRelativeDateUnit.DAY,
              direction = TemplateDateDirection.FUTURE,
              anchor = "date.today",
            ),
            config,
            property,
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_TEMPLATE_VALUE_TYPE_INVALID
    }

    "validateExpression rejects non-positive relative date amount" {
      shouldThrow<InvalidRequestException> {
          WorkItemValueTemplateValidator.validateExpression(
            TemplateValueExpression.RelativeDate(
              amount = 0,
              unit = TemplateRelativeDateUnit.DAY,
              direction = TemplateDateDirection.PAST,
              anchor = "date.now",
            ),
            config,
            null,
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_TEMPLATE_RELATIVE_DATE_AMOUNT_POSITIVE
    }

    "validateExpression rejects unknown relative date anchor" {
      shouldThrow<InvalidRequestException> {
          WorkItemValueTemplateValidator.validateExpression(
            TemplateValueExpression.RelativeDate(
              amount = 1,
              unit = TemplateRelativeDateUnit.DAY,
              direction = TemplateDateDirection.PAST,
              anchor = "date.unknown",
            ),
            config,
            null,
          )
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_TEMPLATE_RELATIVE_DATE_ANCHOR_UNKNOWN
    }

    "validate accepts full template with property and system fields" {
      val template =
        WorkItemValueTemplate(
          target = WorkItemValueTemplateTarget.TRANSITION,
          values =
            mapOf(
              TemplateField.System("title") to TemplateValueExpression.Variable("user.currentUser"),
              TemplateField.Property(apiId = null, code = "dueDate") to
                TemplateValueExpression.Literal(JsonPrimitive("2026-07-04")),
              TemplateField.Property(apiId = null, code = "resolution") to
                TemplateValueExpression.Copy(
                  TemplateField.Property(apiId = null, code = "resolution")
                ),
            ),
        )
      WorkItemValueTemplateValidator.validate(template, config)
    }
  })

private fun sampleConfig(): IssueTypeConfigDetails {
  val configId = UUID.randomUUID()
  val tenantId = UUID.randomUUID()
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
    properties =
      listOf(
        property(configId, tenantId, "dueDate", WorkItemPropertyDataType.DATE),
        property(configId, tenantId, "resolvedAt", WorkItemPropertyDataType.DATETIME),
        property(configId, tenantId, "resolution", WorkItemPropertyDataType.TEXT),
      ),
  )
}

private fun property(
  configId: UUID,
  tenantId: UUID,
  code: String,
  dataType: WorkItemPropertyDataType,
): IssueTypeConfigPropertyRecord =
  IssueTypeConfigPropertyRecord(
    id = UUID.randomUUID(),
    tenantId = tenantId,
    issueTypeConfigId = configId,
    propertyId = UUID.randomUUID(),
    propertyApiId = PublicId.new("fld"),
    code = code,
    name = code,
    dataType = dataType,
    validationOverride = JsonObject(emptyMap()),
    rank = 100,
    displayConfig = JsonObject(emptyMap()),
  )
