package ink.doa.workbench.jobs.messaging

import ink.doa.workbench.core.messaging.DomainEventEnvelope

interface JobRegistration {
  val consumerName: String
  val topic: String
  val eventTypes: Set<String>

  suspend fun handle(envelope: DomainEventEnvelope)
}
