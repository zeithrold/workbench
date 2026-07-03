package ink.doa.workbench.service.messaging.support

import ink.doa.workbench.core.messaging.DomainEventSpec
import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.port.messaging.DomainEventPublisher

data class PublishedEvent<T : Any>(
  val spec: DomainEventSpec<T>,
  val key: String,
  val payload: T,
  val metadata: EventMetadata,
)

class RecordingDomainEventPublisher : DomainEventPublisher {
  private val events = mutableListOf<PublishedEvent<*>>()

  @Suppress("UNCHECKED_CAST")
  val published: List<PublishedEvent<*>>
    get() = events.toList()

  override fun <T : Any> publish(
    spec: DomainEventSpec<T>,
    key: String,
    payload: T,
    metadata: EventMetadata,
  ) {
    events += PublishedEvent(spec, key, payload, metadata)
  }

  fun clear() {
    events.clear()
  }
}
