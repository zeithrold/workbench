package one.ztd.workbench.agile.workitem.activity

import kotlinx.serialization.KSerializer

data class WorkItemActivitySpec<T : Any>(
  val type: WorkItemActivityType,
  val serializer: KSerializer<T>,
)
