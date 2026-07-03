package ink.doa.workbench.core.messaging

import kotlinx.serialization.KSerializer

data class DomainEventSpec<T : Any>(
  val type: String,
  val topic: String,
  val serializer: KSerializer<T>,
  val currentVersion: Int = 1,
)
