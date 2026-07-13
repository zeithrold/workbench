package ink.doa.workbench.agile.workitem.activity

import kotlinx.serialization.json.JsonElement

sealed interface WorkItemActivityPayload {
  data class Created(val value: WorkItemCreatedPayload) : WorkItemActivityPayload

  data class Updated(val value: WorkItemUpdatedPayload) : WorkItemActivityPayload

  data class StatusChanged(val value: WorkItemStatusChangedPayload) : WorkItemActivityPayload

  data class CommentAdded(val value: WorkItemCommentCreatedPayload) : WorkItemActivityPayload

  data class CommentEdited(val value: WorkItemCommentEditedPayload) : WorkItemActivityPayload

  data class CommentDeleted(val value: WorkItemCommentDeletedPayload) : WorkItemActivityPayload

  data class Unknown(val raw: JsonElement) : WorkItemActivityPayload
}
