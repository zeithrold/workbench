package ink.doa.workbench.core.port.messaging

import ink.doa.workbench.core.messaging.DomainEventSpec
import ink.doa.workbench.core.messaging.EventMetadata

/** Writes a domain event durably in the current business transaction. */
interface DomainEventOutbox {
  fun <T : Any> append(
    spec: DomainEventSpec<T>,
    key: String,
    payload: T,
    metadata: EventMetadata = EventMetadata(),
  )
}
