package ink.doa.workbench.core.port.messaging

import ink.doa.workbench.core.messaging.DomainEventSpec
import ink.doa.workbench.core.messaging.EventMetadata

interface DomainEventPublisher {
  fun <T : Any> publish(
    spec: DomainEventSpec<T>,
    key: String,
    payload: T,
    metadata: EventMetadata = EventMetadata(),
  )
}
