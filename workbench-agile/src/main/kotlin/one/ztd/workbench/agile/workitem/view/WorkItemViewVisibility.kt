package one.ztd.workbench.agile.workitem.view

import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode

enum class WorkItemViewVisibility(val dbValue: String) {
  PRIVATE("private"),
  PROJECT("project"),
  TENANT("tenant");

  companion object {
    fun fromDbValue(value: String): WorkItemViewVisibility =
      entries.singleOrNull { it.dbValue == value.lowercase() }
        ?: throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_VIEW_VISIBILITY_UNKNOWN,
          "Unknown work item view visibility: $value",
        )
  }
}
