package ink.doa.workbench.core.workitem.model

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

enum class WorkItemConfigScope(val dbValue: String) {
  TENANT("tenant"),
  PROJECT("project");

  companion object {
    fun fromDbValue(value: String): WorkItemConfigScope =
      entries.singleOrNull { it.dbValue == value.lowercase() || it.name.equals(value, true) }
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_CONFIG_SCOPE_UNKNOWN,
          "Unknown work item config scope: $value",
        )
  }
}

enum class WorkItemStatusGroup(val dbValue: String) {
  TODO("todo"),
  IN_PROGRESS("in_progress"),
  DONE("done");

  companion object {
    fun fromDbValue(value: String): WorkItemStatusGroup =
      entries.singleOrNull { it.dbValue == value.lowercase() || it.name.equals(value, true) }
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_STATUS_GROUP_UNKNOWN,
          "Unknown work item status group: $value",
        )
  }
}

enum class WorkItemPropertyDataType(val dbValue: String) {
  TEXT("text"),
  LONG_TEXT("long_text"),
  NUMBER("number"),
  BOOLEAN("boolean"),
  DATE("date"),
  DATETIME("datetime"),
  SINGLE_SELECT("single_select"),
  MULTI_SELECT("multi_select"),
  USER("user"),
  MULTI_USER("multi_user"),
  PROJECT("project"),
  ISSUE("issue"),
  URL("url"),
  JSON("json");

  companion object {
    fun fromDbValue(value: String): WorkItemPropertyDataType =
      entries.singleOrNull { it.dbValue == value.lowercase() || it.name.equals(value, true) }
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_PROPERTY_TYPE_UNKNOWN,
          "Unknown work item property data type: $value",
        )
  }
}

data class IssueStatusRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val code: String,
  val name: String,
  val statusGroup: WorkItemStatusGroup,
  val rank: Int,
  val color: String?,
  val isTerminal: Boolean,
  val isActive: Boolean,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class PropertyDefinitionRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val code: String,
  val name: String,
  val description: String?,
  val dataType: WorkItemPropertyDataType,
  val isSystem: Boolean,
  val isArray: Boolean,
  val validationSchema: JsonObject,
  val searchConfig: JsonObject,
  val isActive: Boolean,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class IssueTypeRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val projectId: UUID?,
  val scope: WorkItemConfigScope,
  val code: String,
  val name: String,
  val description: String?,
  val icon: String?,
  val color: String?,
  val rank: Int,
  val isActive: Boolean,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class WorkflowRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val code: String,
  val name: String,
  val description: String?,
  val version: Int,
  val isActive: Boolean,
  val publishedAt: OffsetDateTime?,
  val createdBy: UUID?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class WorkflowTransitionRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val workflowId: UUID,
  val name: String,
  val fromStatusId: UUID,
  val fromStatusApiId: PublicId?,
  val toStatusId: UUID,
  val toStatusApiId: PublicId?,
  val rank: Int,
  val permissionCondition: JsonObject,
  val preconditionAst: JsonObject,
  val requiredProperties: JsonElement,
  val optionalProperties: JsonElement,
  val propertyDefaults: JsonObject,
  val isActive: Boolean,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class IssueTypeConfigRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val scope: WorkItemConfigScope,
  val projectId: UUID?,
  val issueTypeId: UUID,
  val issueTypeApiId: PublicId,
  val workflowId: UUID,
  val workflowApiId: PublicId,
  val version: Int,
  val nameOverride: String?,
  val iconOverride: String?,
  val colorOverride: String?,
  val rank: Int,
  val isActive: Boolean,
  val validFrom: OffsetDateTime,
  val validTo: OffsetDateTime?,
  val createdBy: UUID?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class IssueTypeConfigStatusRecord(
  val id: UUID,
  val tenantId: UUID,
  val issueTypeConfigId: UUID,
  val statusId: UUID,
  val statusApiId: PublicId,
  val code: String,
  val name: String,
  val statusGroup: WorkItemStatusGroup,
  val isInitial: Boolean,
  val isTerminal: Boolean,
  val rank: Int,
)

data class IssueTypeConfigPropertyRecord(
  val id: UUID,
  val tenantId: UUID,
  val issueTypeConfigId: UUID,
  val propertyId: UUID,
  val propertyApiId: PublicId,
  val code: String,
  val name: String,
  val dataType: WorkItemPropertyDataType,
  val isRequired: Boolean,
  val defaultValue: JsonElement?,
  val validationOverride: JsonObject,
  val rank: Int,
  val displayConfig: JsonObject,
)

data class IssueTypeConfigDetails(
  val config: IssueTypeConfigRecord,
  val statuses: List<IssueTypeConfigStatusRecord>,
  val properties: List<IssueTypeConfigPropertyRecord>,
)

data class EffectiveIssueTypeConfig(
  val config: IssueTypeConfigDetails,
  val resolvedFrom: WorkItemConfigScope,
)

data class CreateIssueStatusCommand(
  val tenantId: UUID,
  val code: String,
  val name: String,
  val statusGroup: WorkItemStatusGroup,
  val rank: Int = 100,
  val color: String? = null,
  val isTerminal: Boolean = false,
)

data class CreatePropertyDefinitionCommand(
  val tenantId: UUID,
  val code: String,
  val name: String,
  val description: String? = null,
  val dataType: WorkItemPropertyDataType,
  val isArray: Boolean = false,
  val validationSchema: JsonObject = JsonObject(emptyMap()),
  val searchConfig: JsonObject = JsonObject(emptyMap()),
)

data class CreateIssueTypeCommand(
  val tenantId: UUID,
  val scope: WorkItemConfigScope,
  val projectId: UUID? = null,
  val code: String,
  val name: String,
  val description: String? = null,
  val icon: String? = null,
  val color: String? = null,
  val rank: Int = 100,
)

data class CreateWorkflowCommand(
  val tenantId: UUID,
  val code: String,
  val name: String,
  val description: String? = null,
  val createdBy: UUID? = null,
)

data class CreateWorkflowTransitionCommand(
  val tenantId: UUID,
  val workflowApiId: String,
  val name: String,
  val fromStatusApiId: String,
  val toStatusApiId: String,
  val rank: Int = 100,
  val permissionCondition: JsonObject = JsonObject(emptyMap()),
  val preconditionAst: JsonObject = JsonObject(emptyMap()),
  val requiredProperties: JsonElement,
  val optionalProperties: JsonElement,
  val propertyDefaults: JsonObject = JsonObject(emptyMap()),
)

data class IssueTypeConfigStatusInput(
  val statusApiId: String,
  val isInitial: Boolean = false,
  val isTerminal: Boolean = false,
  val rank: Int = 100,
)

data class IssueTypeConfigPropertyInput(
  val propertyApiId: String,
  val isRequired: Boolean = false,
  val defaultValue: JsonElement? = null,
  val validationOverride: JsonObject = JsonObject(emptyMap()),
  val rank: Int = 100,
  val displayConfig: JsonObject = JsonObject(emptyMap()),
)

data class CreateIssueTypeConfigCommand(
  val tenantId: UUID,
  val scope: WorkItemConfigScope,
  val projectId: UUID?,
  val issueTypeApiId: String,
  val workflowApiId: String,
  val nameOverride: String? = null,
  val iconOverride: String? = null,
  val colorOverride: String? = null,
  val rank: Int = 100,
  val createdBy: UUID? = null,
  val statuses: List<IssueTypeConfigStatusInput>,
  val properties: List<IssueTypeConfigPropertyInput> = emptyList(),
)
