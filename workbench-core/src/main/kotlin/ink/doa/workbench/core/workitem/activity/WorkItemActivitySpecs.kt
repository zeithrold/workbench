package ink.doa.workbench.core.workitem.activity

object WorkItemActivitySpecs {
  val Created =
    WorkItemActivitySpec(
      type = WorkItemActivityType.CREATED,
      serializer = WorkItemCreatedPayload.serializer(),
    )

  val Updated =
    WorkItemActivitySpec(
      type = WorkItemActivityType.UPDATED,
      serializer = WorkItemUpdatedPayload.serializer(),
    )

  val StatusChanged =
    WorkItemActivitySpec(
      type = WorkItemActivityType.STATUS_CHANGED,
      serializer = WorkItemStatusChangedPayload.serializer(),
    )

  val CommentCreated =
    WorkItemActivitySpec(
      type = WorkItemActivityType.COMMENT_CREATED,
      serializer = WorkItemCommentCreatedPayload.serializer(),
    )

  private val byType: Map<WorkItemActivityType, WorkItemActivitySpec<*>> =
    listOf(Created, Updated, StatusChanged, CommentCreated).associateBy { it.type }

  fun specFor(type: WorkItemActivityType): WorkItemActivitySpec<*>? = byType[type]
}
