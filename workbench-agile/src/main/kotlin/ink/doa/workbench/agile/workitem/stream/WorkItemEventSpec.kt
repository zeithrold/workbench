package ink.doa.workbench.agile.workitem.stream

import kotlinx.serialization.KSerializer

data class WorkItemEventSpec<T : Any>(
  val type: WorkItemEventType,
  val serializer: KSerializer<T>,
)
