package doa.ink.workbench.core.port.messaging

import doa.ink.workbench.core.messaging.DomainEventSpec
import doa.ink.workbench.core.messaging.EventMetadata

interface DomainEventPublisher {
  fun <T : Any> publish(
    spec: DomainEventSpec<T>,
    key: String,
    payload: T,
    metadata: EventMetadata = EventMetadata(),
  )
}
