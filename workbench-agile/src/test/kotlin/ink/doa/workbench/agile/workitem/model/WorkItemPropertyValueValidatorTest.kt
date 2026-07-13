package ink.doa.workbench.agile.workitem.model

import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class WorkItemPropertyValueValidatorTest :
  StringSpec({
    "accepts null values" {
      WorkItemPropertyValueValidator.validate(property(WorkItemPropertyDataType.TEXT), JsonNull)
    }

    "validates text-like property types" {
      val property = property(WorkItemPropertyDataType.TEXT)
      WorkItemPropertyValueValidator.validate(property, JsonPrimitive("hello"))
      shouldThrow<InvalidRequestException> {
          WorkItemPropertyValueValidator.validate(property, JsonArray(listOf(JsonPrimitive("x"))))
        }
        .errorCode shouldBe WorkbenchErrorCode.WORK_ITEM_PROPERTY_VALUE_INVALID
    }

    "validates date and datetime formats" {
      WorkItemPropertyValueValidator.validate(
        property(WorkItemPropertyDataType.DATE),
        JsonPrimitive("2026-07-04"),
      )
      WorkItemPropertyValueValidator.validate(
        property(WorkItemPropertyDataType.DATETIME),
        JsonPrimitive("2026-07-04T10:15:30Z"),
      )
      shouldThrow<InvalidRequestException> {
        WorkItemPropertyValueValidator.validate(
          property(WorkItemPropertyDataType.DATE),
          JsonPrimitive("not-a-date"),
        )
      }
    }

    "validates id-like property types" {
      val user = property(WorkItemPropertyDataType.USER)
      WorkItemPropertyValueValidator.validate(user, JsonPrimitive("usr_abc"))
      shouldThrow<InvalidRequestException> {
        WorkItemPropertyValueValidator.validate(user, JsonPrimitive(""))
      }
    }

    "validates number and boolean property types" {
      WorkItemPropertyValueValidator.validate(
        property(WorkItemPropertyDataType.NUMBER),
        JsonPrimitive(3.5),
      )
      WorkItemPropertyValueValidator.validate(
        property(WorkItemPropertyDataType.BOOLEAN),
        JsonPrimitive(true),
      )
      shouldThrow<InvalidRequestException> {
        WorkItemPropertyValueValidator.validate(
          property(WorkItemPropertyDataType.NUMBER),
          JsonPrimitive("x"),
        )
      }
    }

    "validates multi-value property types" {
      val multi = property(WorkItemPropertyDataType.MULTI_SELECT)
      WorkItemPropertyValueValidator.validate(
        multi,
        JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))),
      )
      shouldThrow<InvalidRequestException> {
        WorkItemPropertyValueValidator.validate(multi, JsonArray(listOf(JsonPrimitive(""))))
      }
      shouldThrow<InvalidRequestException> {
        WorkItemPropertyValueValidator.validate(multi, JsonPrimitive("single"))
      }
    }

    "accepts any value for json property type" {
      WorkItemPropertyValueValidator.validate(
        property(WorkItemPropertyDataType.JSON),
        JsonObject(mapOf("nested" to JsonPrimitive(true))),
      )
    }
  })

private fun property(dataType: WorkItemPropertyDataType): IssueTypeConfigPropertyRecord {
  val configId = UUID.randomUUID()
  return IssueTypeConfigPropertyRecord(
    id = UUID.randomUUID(),
    tenantId = UUID.randomUUID(),
    issueTypeConfigId = configId,
    propertyId = UUID.randomUUID(),
    propertyApiId = PublicId.new("fld"),
    code = "field",
    name = "Field",
    dataType = dataType,
    validationOverride = JsonObject(emptyMap()),
    rank = 100,
    displayConfig = JsonObject(emptyMap()),
  )
}
