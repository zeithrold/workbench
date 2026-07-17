package one.ztd.workbench.agile.workitem.stream

import one.ztd.workbench.agile.workitem.activity.WorkItemCommentCreatedPayload
import one.ztd.workbench.agile.workitem.activity.WorkItemCommentDeletedPayload
import one.ztd.workbench.agile.workitem.activity.WorkItemCommentEditedPayload
import one.ztd.workbench.agile.workitem.activity.WorkItemCreatedPayload
import one.ztd.workbench.agile.workitem.activity.WorkItemStatusChangedPayload
import one.ztd.workbench.agile.workitem.activity.WorkItemUpdatedPayload

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
