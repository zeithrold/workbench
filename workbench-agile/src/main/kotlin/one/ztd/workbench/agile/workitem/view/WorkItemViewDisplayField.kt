package one.ztd.workbench.agile.workitem.view

import one.ztd.workbench.agile.workitem.query.QueryField

data class WorkItemViewDisplayField(
  val field: QueryField,
  val width: Int? = null,
  val pinned: Boolean = false,
)
