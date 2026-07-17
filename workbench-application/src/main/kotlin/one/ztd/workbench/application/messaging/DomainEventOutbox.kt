package one.ztd.workbench.application.messaging

import one.ztd.workbench.kernel.messaging.DomainEventSpec
import one.ztd.workbench.kernel.messaging.EventMetadata

/** Writes a domain event durably in the current business transaction. */
interface DomainEventOutbox {
  fun <T : Any> append(
    spec: DomainEventSpec<T>,
    key: String,
    payload: T,
    metadata: EventMetadata = EventMetadata(),
  )
}
