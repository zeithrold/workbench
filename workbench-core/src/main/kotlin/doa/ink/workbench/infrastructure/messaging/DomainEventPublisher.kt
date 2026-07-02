package doa.ink.workbench.infrastructure.messaging

interface DomainEventPublisher {
  fun publish(topic: String, key: String, payload: String)
}
