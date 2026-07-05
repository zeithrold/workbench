package ink.doa.workbench.core.workitem.view

import ink.doa.workbench.core.workitem.query.QueryField
import ink.doa.workbench.core.workitem.query.SortDirection

data class WorkItemViewGroupConfig(
  val field: QueryField,
  val direction: SortDirection? = null,
  val collapsed: List<String> = emptyList(),
)

data class WorkItemViewDisplayField(
  val field: QueryField,
  val width: Int? = null,
  val pinned: Boolean = false,
)
