package ink.doa.workbench.web.workitem

import com.fasterxml.jackson.databind.JsonNode
import ink.doa.workbench.agile.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.agile.workitem.model.TransitionRequest
import ink.doa.workbench.agile.workitem.model.UpdateWorkItemCommand
import ink.doa.workbench.web.api.context.ProjectRequestContext
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateWorkItemRequest(
  @field:NotBlank val issueTypeId: String,
  @field:NotBlank @field:Size(max = 500) val title: String,
  val description: RichTextDocumentPayload? = null,
  val assigneeId: String? = null,
  val priorityId: String? = null,
  val sprintId: String? = null,
  val parentId: String? = null,
  val properties: JsonNode? = null,
) {
  fun toCommand(context: ProjectRequestContext, actorUserId: UUID): CreateWorkItemCommand =
    CreateWorkItemCommand(
      tenantId = context.tenant.id,
      projectId = context.project.id,
      issueTypeApiId = issueTypeId,
      title = title,
      description = description?.toDomain(),
      reporterId = actorUserId,
      actorUserId = actorUserId,
      assigneeApiId = assigneeId,
      priorityApiId = priorityId,
      sprintApiId = sprintId,
      parentWorkItemApiId = parentId,
      properties = properties.toJsonObject().toMap(),
    )
}

data class UpdateWorkItemRequest(
  @field:Size(max = 500) val title: String? = null,
  val description: RichTextDocumentPayload? = null,
  val assigneeId: String? = null,
  val priorityId: String? = null,
  val sprintId: String? = null,
  val clearSprint: Boolean = false,
  val properties: JsonNode? = null,
) {
  fun toCommand(
    context: ProjectRequestContext,
    workItemId: String,
    actorUserId: UUID,
  ): UpdateWorkItemCommand =
    UpdateWorkItemCommand(
      tenantId = context.tenant.id,
      projectId = context.project.id,
      workItemApiId = workItemId,
      title = title,
      description = description?.toDomain(),
      assigneeApiId = assigneeId,
      priorityApiId = priorityId,
      sprintApiId = sprintId,
      clearSprint = clearSprint,
      properties = properties.toJsonObject().toMap(),
      actorUserId = actorUserId,
    )
}

data class TransitionWorkItemRequest(
  @field:NotBlank val transitionId: String,
  val title: String? = null,
  val description: RichTextDocumentPayload? = null,
  val comment: RichTextDocumentPayload? = null,
  val properties: JsonNode? = null,
) {
  fun toCommand(
    context: ProjectRequestContext,
    workItemId: String,
    actorUserId: UUID,
    actorUserApiId: String,
  ): TransitionRequest =
    TransitionRequest(
      tenantId = context.tenant.id,
      projectId = context.project.id,
      workItemApiId = workItemId,
      transitionApiId = transitionId,
      actorUserId = actorUserId,
      actorUserApiId = actorUserApiId,
      title = title,
      description = description?.toDomain(),
      comment = comment?.toDomain(),
      properties = properties.toJsonObject().toMap(),
    )
}

data class DeleteWorkItemRequest(val deleteReason: String? = null)
