package one.ztd.workbench.data.repository.workitem

import java.util.UUID
import one.ztd.workbench.agile.workitem.activity.WorkItemActivityCommentRef
import one.ztd.workbench.agile.workitem.activity.WorkItemActivityStatusSnapshot
import one.ztd.workbench.agile.workitem.activity.WorkItemCommentCreatedPayload
import one.ztd.workbench.agile.workitem.activity.WorkItemCommentDeletedPayload
import one.ztd.workbench.agile.workitem.activity.WorkItemCommentEditedPayload
import one.ztd.workbench.agile.workitem.activity.WorkItemCreatedPayload
import one.ztd.workbench.agile.workitem.activity.WorkItemStatusChangedPayload
import one.ztd.workbench.agile.workitem.activity.WorkItemUpdatedPayload
import one.ztd.workbench.agile.workitem.stream.AppendWorkItemEventCommand
import one.ztd.workbench.agile.workitem.stream.WorkItemEventSpecs
import one.ztd.workbench.data.persistence.postgres.workitem.loadIssueTypeRef
import one.ztd.workbench.data.persistence.postgres.workitem.loadStatusRef
import one.ztd.workbench.data.persistence.postgres.workitem.loadTransitionRef
import org.springframework.stereotype.Component

@Component
class WorkItemEventFactory {
  fun created(
    context: WorkItemActivityContext,
    issueTypeId: UUID,
    initialStatusId: UUID,
  ): AppendWorkItemEventCommand<WorkItemCreatedPayload> {
    val statusRef = loadStatusRef(initialStatusId)
    val issueTypeRef = loadIssueTypeRef(issueTypeId)
    val payload =
      WorkItemCreatedPayload(
        status = WorkItemActivityStatusSnapshot(from = null, to = statusRef),
        issueType = issueTypeRef,
      )
    return AppendWorkItemEventCommand(
      tenantId = context.tenantId,
      projectId = context.projectId,
      workItemId = context.workItemId,
      actorUserId = context.actorUserId,
      spec = WorkItemEventSpecs.Created,
      payload = payload,
      occurredAt = context.occurredAt,
      summary = "Created with status ${statusRef.name}",
    )
  }

  fun updated(
    input: WorkItemUpdateActivityInput
  ): AppendWorkItemEventCommand<WorkItemUpdatedPayload>? {
    val fields =
      buildWorkItemFieldChanges(
        before = input.before,
        after = input.after,
        command = input.command,
        propertyValues = input.propertyValues,
      )
    if (fields.isEmpty()) return null
    val payload = WorkItemUpdatedPayload(fields = fields)
    return AppendWorkItemEventCommand(
      tenantId = input.context.tenantId,
      projectId = input.context.projectId,
      workItemId = input.context.workItemId,
      actorUserId = input.context.actorUserId,
      spec = WorkItemEventSpecs.Updated,
      payload = payload,
      occurredAt = input.context.occurredAt,
      summary = "Updated ${fields.size} field(s)",
    )
  }

  fun statusChanged(
    input: WorkItemStatusChangedInput
  ): AppendWorkItemEventCommand<WorkItemStatusChangedPayload> {
    val fromRef = loadStatusRef(input.fromStatusId)
    val toRef = loadStatusRef(input.toStatusId)
    val transitionRef = loadTransitionRef(input.transitionId)
    val payload =
      WorkItemStatusChangedPayload(
        status =
          WorkItemActivityStatusSnapshot(
            from = fromRef,
            to = toRef,
            transition = transitionRef,
          )
      )
    return AppendWorkItemEventCommand(
      tenantId = input.context.tenantId,
      projectId = input.context.projectId,
      workItemId = input.context.workItemId,
      actorUserId = input.context.actorUserId,
      spec = WorkItemEventSpecs.StatusChanged,
      payload = payload,
      occurredAt = input.context.occurredAt,
      summary = "Moved from ${fromRef.name} to ${toRef.name}",
    )
  }

  fun commentAdded(
    input: WorkItemCommentCreatedInput
  ): AppendWorkItemEventCommand<WorkItemCommentCreatedPayload> {
    val preview = truncatePreview(input.plainTextPreview)
    val payload =
      WorkItemCommentCreatedPayload(
        comment = WorkItemActivityCommentRef(id = input.commentApiId, plainTextPreview = preview)
      )
    return AppendWorkItemEventCommand(
      tenantId = input.context.tenantId,
      projectId = input.context.projectId,
      workItemId = input.context.workItemId,
      actorUserId = input.context.actorUserId,
      spec = WorkItemEventSpecs.CommentAdded,
      payload = payload,
      occurredAt = input.context.occurredAt,
      summary = preview,
      sourceId = input.commentApiId,
    )
  }

  fun commentEdited(
    input: WorkItemCommentCreatedInput
  ): AppendWorkItemEventCommand<WorkItemCommentEditedPayload> {
    val preview = truncatePreview(input.plainTextPreview)
    val payload =
      WorkItemCommentEditedPayload(
        comment = WorkItemActivityCommentRef(id = input.commentApiId, plainTextPreview = preview)
      )
    return AppendWorkItemEventCommand(
      tenantId = input.context.tenantId,
      projectId = input.context.projectId,
      workItemId = input.context.workItemId,
      actorUserId = input.context.actorUserId,
      spec = WorkItemEventSpecs.CommentEdited,
      payload = payload,
      occurredAt = input.context.occurredAt,
      summary = preview,
      sourceId = input.commentApiId,
    )
  }

  fun commentDeleted(
    input: WorkItemCommentDeletedInput
  ): AppendWorkItemEventCommand<WorkItemCommentDeletedPayload> {
    val preview = truncatePreview(input.plainTextPreview)
    val payload =
      WorkItemCommentDeletedPayload(
        comment = WorkItemActivityCommentRef(id = input.commentApiId, plainTextPreview = preview),
        deleteReason = input.deleteReason,
      )
    return AppendWorkItemEventCommand(
      tenantId = input.context.tenantId,
      projectId = input.context.projectId,
      workItemId = input.context.workItemId,
      actorUserId = input.context.actorUserId,
      spec = WorkItemEventSpecs.CommentDeleted,
      payload = payload,
      occurredAt = input.context.occurredAt,
      summary = "Deleted comment",
      sourceId = input.commentApiId,
    )
  }

  private fun truncatePreview(value: String?): String {
    val normalized = value?.trim().orEmpty()
    require(normalized.isNotEmpty()) { "Comment preview must not be empty." }
    return normalized.take(PREVIEW_MAX_LENGTH)
  }

  private companion object {
    const val PREVIEW_MAX_LENGTH = 200
  }
}
