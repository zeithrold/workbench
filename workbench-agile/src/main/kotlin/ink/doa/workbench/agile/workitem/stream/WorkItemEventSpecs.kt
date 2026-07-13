package ink.doa.workbench.agile.workitem.stream

import ink.doa.workbench.agile.workitem.activity.WorkItemCommentCreatedPayload
import ink.doa.workbench.agile.workitem.activity.WorkItemCommentDeletedPayload
import ink.doa.workbench.agile.workitem.activity.WorkItemCommentEditedPayload
import ink.doa.workbench.agile.workitem.activity.WorkItemCreatedPayload
import ink.doa.workbench.agile.workitem.activity.WorkItemStatusChangedPayload
import ink.doa.workbench.agile.workitem.activity.WorkItemUpdatedPayload

object WorkItemEventSpecs {
  val Created =
    WorkItemEventSpec(
      type = WorkItemEventType.CREATED,
      serializer = WorkItemCreatedPayload.serializer(),
    )

  val Updated =
    WorkItemEventSpec(
      type = WorkItemEventType.UPDATED,
      serializer = WorkItemUpdatedPayload.serializer(),
    )

  val StatusChanged =
    WorkItemEventSpec(
      type = WorkItemEventType.STATUS_CHANGED,
      serializer = WorkItemStatusChangedPayload.serializer(),
    )

  val CommentAdded =
    WorkItemEventSpec(
      type = WorkItemEventType.COMMENT_ADDED,
      serializer = WorkItemCommentCreatedPayload.serializer(),
    )

  val CommentEdited =
    WorkItemEventSpec(
      type = WorkItemEventType.COMMENT_EDITED,
      serializer = WorkItemCommentEditedPayload.serializer(),
    )

  val CommentDeleted =
    WorkItemEventSpec(
      type = WorkItemEventType.COMMENT_DELETED,
      serializer = WorkItemCommentDeletedPayload.serializer(),
    )

  private val byType: Map<WorkItemEventType, WorkItemEventSpec<*>> =
    listOf(Created, Updated, StatusChanged, CommentAdded, CommentEdited, CommentDeleted)
      .associateBy { it.type }

  fun specFor(type: WorkItemEventType): WorkItemEventSpec<*>? = byType[type]
}
