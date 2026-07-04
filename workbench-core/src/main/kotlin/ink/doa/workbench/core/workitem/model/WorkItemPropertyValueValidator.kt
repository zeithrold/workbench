package ink.doa.workbench.core.workitem.model

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

object WorkItemPropertyValueValidator {
  private typealias PropertyValueValidator = (JsonElement) -> Boolean

  private val validators: Map<WorkItemPropertyDataType, PropertyValueValidator> =
    mapOf(
      WorkItemPropertyDataType.TEXT to { it.isStringPrimitive() },
      WorkItemPropertyDataType.LONG_TEXT to { it.isStringPrimitive() },
      WorkItemPropertyDataType.URL to { it.isStringPrimitive() },
      WorkItemPropertyDataType.DATE to { it.isStringPrimitive() && it.parsesAsDate() },
      WorkItemPropertyDataType.DATETIME to { it.isStringPrimitive() && it.parsesAsDateTime() },
      WorkItemPropertyDataType.USER to { it.isNonBlankStringPrimitive() },
      WorkItemPropertyDataType.PROJECT to { it.isNonBlankStringPrimitive() },
      WorkItemPropertyDataType.ISSUE to { it.isNonBlankStringPrimitive() },
      WorkItemPropertyDataType.SINGLE_SELECT to { it.isNonBlankStringPrimitive() },
      WorkItemPropertyDataType.NUMBER to { it is JsonPrimitive && it.doubleOrNull != null },
      WorkItemPropertyDataType.BOOLEAN to { it is JsonPrimitive && it.booleanOrNull != null },
      WorkItemPropertyDataType.MULTI_SELECT to { it.isArrayOfNonBlankStrings() },
      WorkItemPropertyDataType.MULTI_USER to { it.isArrayOfNonBlankStrings() },
      WorkItemPropertyDataType.JSON to { true },
    )

  fun validate(property: IssueTypeConfigPropertyRecord, value: JsonElement) {
    if (value is JsonNull) return
    val valid = validators.getValue(property.dataType).invoke(value)
    if (!valid) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_PROPERTY_VALUE_INVALID,
        "Invalid value for property ${property.code}.",
      )
    }
  }

  private fun JsonElement.isStringPrimitive(): Boolean =
    this is JsonPrimitive && contentOrNull != null

  private fun JsonElement.isNonBlankStringPrimitive(): Boolean =
    this is JsonPrimitive && !contentOrNull.isNullOrBlank()

  private fun JsonElement.isArrayOfNonBlankStrings(): Boolean =
    this is JsonArray && all { it.isNonBlankStringPrimitive() }

  private fun JsonElement.parsesAsDate(): Boolean =
    runCatching { LocalDate.parse((this as JsonPrimitive).content) }.isSuccess

  private fun JsonElement.parsesAsDateTime(): Boolean =
    runCatching { OffsetDateTime.parse((this as JsonPrimitive).content) }.isSuccess
}
