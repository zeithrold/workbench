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
  @Suppress("CyclomaticComplexMethod")
  fun validate(property: IssueTypeConfigPropertyRecord, value: JsonElement) {
    if (value is JsonNull) return
    val valid =
      when (property.dataType) {
        WorkItemPropertyDataType.TEXT,
        WorkItemPropertyDataType.LONG_TEXT,
        WorkItemPropertyDataType.URL -> value.isStringPrimitive()
        WorkItemPropertyDataType.DATE -> value.isStringPrimitive() && parsesAsDate(value)
        WorkItemPropertyDataType.DATETIME -> value.isStringPrimitive() && parsesAsDateTime(value)
        WorkItemPropertyDataType.USER,
        WorkItemPropertyDataType.PROJECT,
        WorkItemPropertyDataType.ISSUE,
        WorkItemPropertyDataType.SINGLE_SELECT -> value.isNonBlankStringPrimitive()
        WorkItemPropertyDataType.NUMBER -> value is JsonPrimitive && value.doubleOrNull != null
        WorkItemPropertyDataType.BOOLEAN -> value is JsonPrimitive && value.booleanOrNull != null
        WorkItemPropertyDataType.MULTI_SELECT,
        WorkItemPropertyDataType.MULTI_USER -> value.isArrayOfNonBlankStrings()
        WorkItemPropertyDataType.JSON -> true
      }
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

  private fun parsesAsDate(value: JsonElement): Boolean =
    runCatching { LocalDate.parse((value as JsonPrimitive).content) }.isSuccess

  private fun parsesAsDateTime(value: JsonElement): Boolean =
    runCatching { OffsetDateTime.parse((value as JsonPrimitive).content) }.isSuccess
}
