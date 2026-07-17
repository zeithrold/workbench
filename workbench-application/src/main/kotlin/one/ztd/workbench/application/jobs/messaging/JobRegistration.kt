package one.ztd.workbench.application.jobs.messaging

import one.ztd.workbench.kernel.messaging.DomainEventEnvelope

interface JobRegistration {
  val consumerName: String
  val topic: String
  val eventTypes: Set<String>

  suspend fun handle(envelope: DomainEventEnvelope)
}
