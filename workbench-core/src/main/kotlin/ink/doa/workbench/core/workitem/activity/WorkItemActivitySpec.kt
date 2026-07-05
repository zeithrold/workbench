package ink.doa.workbench.core.workitem.activity

import kotlinx.serialization.KSerializer

data class WorkItemActivitySpec<T : Any>(
  val type: WorkItemActivityType,
  val serializer: KSerializer<T>,
)
