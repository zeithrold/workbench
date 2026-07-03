package doa.ink.workbench.core.tenant.events

import doa.ink.workbench.core.messaging.DomainEventSpec
import doa.ink.workbench.core.messaging.DomainTopics

object TenantDomainEvents {
  val Created =
    DomainEventSpec(
      type = "tenant.created",
      topic = DomainTopics.TENANT,
      serializer = TenantCreatedEvent.serializer(),
    )

  val DestroyRequested =
    DomainEventSpec(
      type = "tenant.destroy_requested",
      topic = DomainTopics.TENANT,
      serializer = TenantDestroyRequestedEvent.serializer(),
    )

  val Destroyed =
    DomainEventSpec(
      type = "tenant.destroyed",
      topic = DomainTopics.TENANT,
      serializer = TenantDestroyedEvent.serializer(),
    )
}
