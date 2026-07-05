package ink.doa.workbench.core.workitem.activity

enum class WorkItemActivityType(val dbValue: String) {
  CREATED("work_item.created"),
  UPDATED("work_item.updated"),
  STATUS_CHANGED("work_item.status_changed"),
  COMMENT_CREATED("work_item.comment.created"),
  UNKNOWN("unknown");

  companion object {
    fun fromDbValue(value: String): WorkItemActivityType =
      entries.firstOrNull { it.dbValue == value } ?: UNKNOWN

    fun requireKnown(value: WorkItemActivityType): WorkItemActivityType {
      require(value != UNKNOWN) { "Activity type must be known for write operations." }
      return value
    }
  }
}

enum class WorkItemActivitySourceType(val dbValue: String) {
  USER("user"),
  SYSTEM("system"),
  AUTOMATION("automation");

  companion object {
    fun fromDbValue(value: String): WorkItemActivitySourceType =
      entries.firstOrNull { it.dbValue == value } ?: USER
  }
}
