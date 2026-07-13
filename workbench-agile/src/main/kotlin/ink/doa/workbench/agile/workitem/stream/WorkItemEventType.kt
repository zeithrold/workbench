package ink.doa.workbench.agile.workitem.stream

enum class WorkItemEventType(val dbValue: String) {
  CREATED("work_item.created"),
  UPDATED("work_item.updated"),
  STATUS_CHANGED("work_item.status_changed"),
  COMMENT_ADDED("comment.added"),
  COMMENT_EDITED("comment.edited"),
  COMMENT_DELETED("comment.deleted"),
  UNKNOWN("unknown");

  fun isCommentEvent(): Boolean =
    this == COMMENT_ADDED || this == COMMENT_EDITED || this == COMMENT_DELETED

  companion object {
    fun fromDbValue(value: String): WorkItemEventType =
      entries.firstOrNull { it.dbValue == value } ?: UNKNOWN

    fun requireKnown(value: WorkItemEventType): WorkItemEventType {
      require(value != UNKNOWN) { "Work item event type must be known for write operations." }
      return value
    }

    /** Maps legacy activity rows during migration. */
    fun fromLegacyActivityType(value: String): WorkItemEventType? =
      when (value) {
        "work_item.created" -> CREATED
        "work_item.updated" -> UPDATED
        "work_item.status_changed" -> STATUS_CHANGED
        "work_item.comment.created" -> null
        else -> null
      }
  }
}

enum class WorkItemEventSourceType(val dbValue: String) {
  USER("user"),
  SYSTEM("system"),
  AUTOMATION("automation");

  companion object {
    fun fromDbValue(value: String): WorkItemEventSourceType =
      entries.firstOrNull { it.dbValue == value } ?: USER
  }
}
