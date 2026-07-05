package ink.doa.workbench.web.workitem

import com.fasterxml.jackson.databind.JsonNode
import ink.doa.workbench.core.common.context.ProjectRequestContext
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.TransitionWorkItemCommand
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateWorkItemRequest(
  @field:NotBlank val issueTypeId: String,
  @field:NotBlank @field:Size(max = 500) val title: String,
  val description: String? = null,
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
      description = description,
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
  val description: String? = null,
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
      description = description,
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
  val description: String? = null,
  val comment: String? = null,
  val properties: JsonNode? = null,
) {
  fun toCommand(
    context: ProjectRequestContext,
    workItemId: String,
    actorUserId: UUID,
  ): TransitionWorkItemCommand =
    TransitionWorkItemCommand(
      tenantId = context.tenant.id,
      projectId = context.project.id,
      workItemApiId = workItemId,
      transitionApiId = transitionId,
      actorUserId = actorUserId,
      title = title,
      description = description,
      comment = comment,
      properties = properties.toJsonObject().toMap(),
    )
}

data class DeleteWorkItemRequest(val deleteReason: String? = null)

data class WorkItemSearchRequest(
  val query: JsonNode,
  val limit: Int? = null,
  val offset: Long? = null,
)
