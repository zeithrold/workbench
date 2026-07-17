package one.ztd.workbench.kernel.port.messaging

import one.ztd.workbench.kernel.messaging.DomainEventSpec
import one.ztd.workbench.kernel.messaging.EventMetadata

interface DomainEventPublisher {
  fun <T : Any> publish(
    spec: DomainEventSpec<T>,
    key: String,
    payload: T,
    metadata: EventMetadata = EventMetadata(),
  )
}
