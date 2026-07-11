package ink.doa.workbench.web.workitem

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import ink.doa.workbench.agile.workitem.WorkItemAccessRulePresentation
import ink.doa.workbench.core.workitem.model.EffectiveIssueTypeConfig
import ink.doa.workbench.core.workitem.model.IssueStatusRecord
import ink.doa.workbench.core.workitem.model.IssueSubtypeConstraintRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.IssueTypeConfigPropertyRecord
import ink.doa.workbench.core.workitem.model.IssueTypeConfigStatusRecord
import ink.doa.workbench.core.workitem.model.IssueTypeRecord
import ink.doa.workbench.core.workitem.model.PropertyDefinitionRecord
import ink.doa.workbench.core.workitem.model.WorkItemAttachmentRecord
import ink.doa.workbench.core.workitem.model.WorkItemCommentRecord
import ink.doa.workbench.core.workitem.model.WorkflowRecord
import ink.doa.workbench.core.workitem.model.WorkflowTransitionRecord
import jakarta.validation.constraints.NotBlank
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

data class CreateIssueStatusRequest(
  @field:NotBlank val code: String,
  @field:NotBlank val name: String,
  @field:NotBlank val statusGroup: String,
  val rank: Int? = null,
  val color: String? = null,
  val isTerminal: Boolean? = null,
)

data class CreatePropertyDefinitionRequest(
  @field:NotBlank val code: String,
  @field:NotBlank val name: String,
  @field:NotBlank val dataType: String,
  val description: String? = null,
  val isArray: Boolean? = null,
  val validationSchema: JsonNode? = null,
  val searchConfig: JsonNode? = null,
)

data class CreateIssueTypeRequest(
  @field:NotBlank val scope: String,
  val projectId: String? = null,
  @field:NotBlank val code: String,
  @field:NotBlank val name: String,
  val description: String? = null,
  val icon: String? = null,
  val color: String? = null,
  val rank: Int? = null,
)

data class CreateWorkflowRequest(
  @field:NotBlank val code: String,
  @field:NotBlank val name: String,
  val description: String? = null,
)

data class CreateWorkflowTransitionRequest(
  @field:NotBlank val name: String,
  val fromStatusId: String? = null,
  @field:NotBlank val toStatusId: String,
  val rank: Int? = null,
  val preconditionAst: JsonNode? = null,
  val fields: JsonNode? = null,
)

data class CreateIssueTypeConfigRequest(
  @field:NotBlank val scope: String,
  val projectId: String? = null,
  @field:NotBlank val issueTypeId: String,
  @field:NotBlank val workflowId: String,
  val nameOverride: String? = null,
  val iconOverride: String? = null,
  val colorOverride: String? = null,
  val rank: Int? = null,
  val createFields: JsonNode,
  val statuses: List<TypeConfigStatusRequest>,
  val properties: List<TypeConfigPropertyRequest>? = null,
)

data class CreateIssueSubtypeConstraintRequest(
  val projectId: String? = null,
  @field:NotBlank val parentIssueTypeId: String,
  @field:NotBlank val childIssueTypeId: String,
  val isDefault: Boolean? = null,
  val minChildren: Int? = null,
  val maxChildren: Int? = null,
)

data class TypeConfigStatusRequest(
  @field:NotBlank val statusId: String,
  val isInitial: Boolean? = null,
  val isTerminal: Boolean? = null,
  val rank: Int? = null,
)

data class TypeConfigPropertyRequest(
  @field:NotBlank val propertyId: String,
  val validationOverride: JsonNode? = null,
  val rank: Int? = null,
  val displayConfig: JsonNode? = null,
)

data class IssueStatusResponse(
  val id: String,
  val code: String,
  val name: String,
  val statusGroup: String,
  val rank: Int,
  val color: String?,
  val isTerminal: Boolean,
) {
  companion object {
    fun from(record: IssueStatusRecord) =
      IssueStatusResponse(
        id = record.apiId.value,
        code = record.code,
        name = record.name,
        statusGroup = record.statusGroup.dbValue,
        rank = record.rank,
        color = record.color,
        isTerminal = record.isTerminal,
      )
  }
}

data class PropertyDefinitionResponse(
  val id: String,
  val code: String,
  val name: String,
  val description: String?,
  val dataType: String,
  val isArray: Boolean,
  val validationSchema: JsonObject,
  val searchConfig: JsonObject,
) {
  companion object {
    fun from(record: PropertyDefinitionRecord) =
      PropertyDefinitionResponse(
        id = record.apiId.value,
        code = record.code,
        name = record.name,
        description = record.description,
        dataType = record.dataType.dbValue,
        isArray = record.isArray,
        validationSchema = record.validationSchema,
        searchConfig = record.searchConfig,
      )
  }
}

data class IssueTypeResponse(
  val id: String,
  val scope: String,
  val code: String,
  val name: String,
  val description: String?,
  val icon: String?,
  val color: String?,
  val rank: Int,
) {
  companion object {
    fun from(record: IssueTypeRecord) =
      IssueTypeResponse(
        id = record.apiId.value,
        scope = record.scope.dbValue,
        code = record.code,
        name = record.name,
        description = record.description,
        icon = record.icon,
        color = record.color,
        rank = record.rank,
      )
  }
}

data class WorkflowResponse(
  val id: String,
  val code: String,
  val name: String,
  val description: String?,
  val version: Int,
  val publishedAt: String?,
) {
  companion object {
    fun from(record: WorkflowRecord) =
      WorkflowResponse(
        id = record.apiId.value,
        code = record.code,
        name = record.name,
        description = record.description,
        version = record.version,
        publishedAt = record.publishedAt?.toString(),
      )
  }
}

data class WorkflowTransitionResponse(
  val id: String,
  val name: String,
  val fromStatusId: String?,
  val toStatusId: String?,
  val rank: Int,
  val preconditionAst: JsonObject,
  val fields: JsonObject,
) {
  companion object {
    fun from(record: WorkflowTransitionRecord) =
      WorkflowTransitionResponse(
        id = record.apiId.value,
        name = record.name,
        fromStatusId = record.fromStatusApiId?.value,
        toStatusId = record.toStatusApiId?.value,
        rank = record.rank,
        preconditionAst = record.preconditionAst,
        fields = record.fields,
      )
  }
}

data class IssueTypeConfigResponse(
  val id: String,
  val scope: String,
  val issueTypeId: String,
  val workflowId: String,
  val version: Int,
  val nameOverride: String?,
  val iconOverride: String?,
  val colorOverride: String?,
  val rank: Int,
  val createFields: JsonObject,
  val statuses: List<IssueTypeConfigStatusResponse>,
  val properties: List<IssueTypeConfigPropertyResponse>,
) {
  companion object {
    fun from(details: IssueTypeConfigDetails) =
      IssueTypeConfigResponse(
        id = details.config.apiId.value,
        scope = details.config.scope.dbValue,
        issueTypeId = details.config.issueTypeApiId.value,
        workflowId = details.config.workflowApiId.value,
        version = details.config.version,
        nameOverride = details.config.nameOverride,
        iconOverride = details.config.iconOverride,
        colorOverride = details.config.colorOverride,
        rank = details.config.rank,
        createFields = details.config.createFields,
        statuses = details.statuses.map(IssueTypeConfigStatusResponse::from),
        properties = details.properties.map(IssueTypeConfigPropertyResponse::from),
      )
  }
}

data class IssueTypeConfigStatusResponse(
  val statusId: String,
  val code: String,
  val name: String,
  val statusGroup: String,
  val isInitial: Boolean,
  val isTerminal: Boolean,
  val rank: Int,
) {
  companion object {
    fun from(record: IssueTypeConfigStatusRecord) =
      IssueTypeConfigStatusResponse(
        statusId = record.statusApiId.value,
        code = record.code,
        name = record.name,
        statusGroup = record.statusGroup.dbValue,
        isInitial = record.isInitial,
        isTerminal = record.isTerminal,
        rank = record.rank,
      )
  }
}

data class IssueTypeConfigPropertyResponse(
  val propertyId: String,
  val code: String,
  val name: String,
  val dataType: String,
  val validationOverride: JsonObject,
  val rank: Int,
  val displayConfig: JsonObject,
) {
  companion object {
    fun from(record: IssueTypeConfigPropertyRecord) =
      IssueTypeConfigPropertyResponse(
        propertyId = record.propertyApiId.value,
        code = record.code,
        name = record.name,
        dataType = record.dataType.dbValue,
        validationOverride = record.validationOverride,
        rank = record.rank,
        displayConfig = record.displayConfig,
      )
  }
}

data class EffectiveIssueTypeConfigResponse(
  val resolvedFrom: String,
  val config: IssueTypeConfigResponse,
) {
  companion object {
    fun from(record: EffectiveIssueTypeConfig) =
      EffectiveIssueTypeConfigResponse(
        resolvedFrom = record.resolvedFrom.dbValue,
        config = IssueTypeConfigResponse.from(record.config),
      )
  }
}

data class IssueSubtypeConstraintResponse(
  val id: String,
  val projectId: String?,
  val parentIssueTypeId: String,
  val childIssueTypeId: String,
  val isDefault: Boolean,
  val minChildren: Int?,
  val maxChildren: Int?,
) {
  companion object {
    fun from(record: IssueSubtypeConstraintRecord): IssueSubtypeConstraintResponse =
      IssueSubtypeConstraintResponse(
        id = record.id.toString(),
        projectId = record.projectId?.toString(),
        parentIssueTypeId = record.parentIssueTypeApiId.value,
        childIssueTypeId = record.childIssueTypeApiId.value,
        isDefault = record.isDefault,
        minChildren = record.minChildren,
        maxChildren = record.maxChildren,
      )
  }
}

internal fun JsonNode?.toJsonElement(objectMapper: ObjectMapper = ObjectMapper()): JsonElement =
  this?.let { Json.parseToJsonElement(objectMapper.writeValueAsString(it)) }
    ?: Json.parseToJsonElement("{}")

internal fun JsonNode?.toJsonObject(objectMapper: ObjectMapper = ObjectMapper()): JsonObject =
  toJsonElement(objectMapper).jsonObject

internal fun JsonObject.toMap(): Map<String, JsonElement> = entries.associate { it.key to it.value }

data class CreateWorkItemCommentRequest(val body: RichTextDocumentPayload)

data class UpdateWorkItemCommentRequest(val body: RichTextDocumentPayload)

data class WorkItemCommentResponse(
  val id: String,
  val body: RichTextDocumentPayload,
  val authorId: String,
  val createdAt: String,
  val updatedAt: String,
  val editedAt: String?,
) {
  companion object {
    fun from(record: WorkItemCommentRecord): WorkItemCommentResponse =
      WorkItemCommentResponse(
        id = record.apiId.value,
        body = RichTextDocumentPayload.from(record.body),
        authorId = record.authorApiId.value,
        createdAt = record.createdAt.toString(),
        updatedAt = record.updatedAt.toString(),
        editedAt = record.editedAt?.toString(),
      )
  }
}

data class InitiateWorkItemAttachmentUploadRequest(
  @field:NotBlank val filename: String,
  val contentType: String?,
  val byteSize: Long,
  val purpose: String = "standalone",
  val commentId: String? = null,
)

data class WorkItemAttachmentUploadSessionResponse(
  val id: String,
  val uploadUrl: String,
  val uploadMethod: String,
  val expiresAt: String,
  val requiredHeaders: Map<String, String>,
) {
  companion object {
    fun from(session: ink.doa.workbench.agile.workitem.WorkItemAttachmentUploadSession) =
      WorkItemAttachmentUploadSessionResponse(
        id = session.attachmentApiId,
        uploadUrl = session.presigned.url,
        uploadMethod = session.presigned.method,
        expiresAt = session.presigned.expiresAt.toString(),
        requiredHeaders = session.presigned.headers,
      )
  }
}

data class CreateWorkItemAccessRuleRequest(
  @field:NotBlank val subjectType: String,
  val subjectUserId: String? = null,
  val subjectGroupId: String? = null,
  val subjectRoleCode: String? = null,
  @field:NotBlank val actionType: String,
  val transitionId: String? = null,
  val fieldKey: String? = null,
  @field:NotBlank val effect: String,
  val condition: JsonNode? = null,
  val rank: Int? = null,
)

data class WorkItemAccessRuleResponse(
  val id: String,
  val subjectType: String,
  val subjectUserId: String?,
  val subjectGroupId: String?,
  val subjectRoleCode: String?,
  val actionType: String,
  val transitionId: String?,
  val fieldKey: String?,
  val effect: String,
  val condition: JsonObject,
  val rank: Int,
) {
  companion object {
    fun from(record: WorkItemAccessRulePresentation) =
      WorkItemAccessRuleResponse(
        id = record.id,
        subjectType = record.subjectType,
        subjectUserId = record.subjectUserId,
        subjectGroupId = record.subjectGroupId,
        subjectRoleCode = record.subjectRoleCode,
        actionType = record.actionType,
        transitionId = record.transitionId,
        fieldKey = record.fieldKey,
        effect = record.effect,
        condition = record.condition,
        rank = record.rank,
      )
  }
}

data class WorkItemAttachmentResponse(
  val id: String,
  val filename: String,
  val contentType: String?,
  val byteSize: Long,
  val purpose: String,
  val commentId: String?,
  val uploadedBy: String,
  val createdAt: String,
  val downloadUrl: String,
) {
  companion object {
    fun from(
      record: WorkItemAttachmentRecord,
      downloadUrl: String,
    ): WorkItemAttachmentResponse =
      WorkItemAttachmentResponse(
        id = record.apiId.value,
        filename = record.filename,
        contentType = record.contentType,
        byteSize = record.byteSize,
        purpose = record.purpose.wireValue,
        commentId = record.commentApiId?.value,
        uploadedBy = record.uploadedByApiId.value,
        createdAt = record.createdAt.toString(),
        downloadUrl = downloadUrl,
      )
  }
}
