package doa.ink.workbench.core.port.messaging

interface DomainEventPublisher {
  fun publish(topic: String, key: String, payload: String)
}
