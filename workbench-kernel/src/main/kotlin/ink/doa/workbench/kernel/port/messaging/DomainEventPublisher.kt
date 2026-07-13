package ink.doa.workbench.kernel.port.messaging

import ink.doa.workbench.kernel.messaging.DomainEventSpec
import ink.doa.workbench.kernel.messaging.EventMetadata

interface DomainEventPublisher {
  fun <T : Any> publish(
    spec: DomainEventSpec<T>,
    key: String,
    payload: T,
    metadata: EventMetadata = EventMetadata(),
  )
}
