package ink.doa.workbench.core.workitem.view

import ink.doa.workbench.core.workitem.query.QueryField

data class WorkItemViewDisplayField(
  val field: QueryField,
  val width: Int? = null,
  val pinned: Boolean = false,
)
