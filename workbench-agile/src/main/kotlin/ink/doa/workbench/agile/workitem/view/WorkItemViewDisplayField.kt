package ink.doa.workbench.agile.workitem.view

import ink.doa.workbench.agile.workitem.query.QueryField

data class WorkItemViewDisplayField(
  val field: QueryField,
  val width: Int? = null,
  val pinned: Boolean = false,
)
