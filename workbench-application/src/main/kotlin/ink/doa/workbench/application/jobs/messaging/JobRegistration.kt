package ink.doa.workbench.application.jobs.messaging

import ink.doa.workbench.kernel.messaging.DomainEventEnvelope

interface JobRegistration {
  val consumerName: String
  val topic: String
  val eventTypes: Set<String>

  suspend fun handle(envelope: DomainEventEnvelope)
}
