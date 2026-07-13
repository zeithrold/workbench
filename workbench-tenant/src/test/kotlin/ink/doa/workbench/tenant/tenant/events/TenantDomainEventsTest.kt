package ink.doa.workbench.tenant.tenant.events

import ink.doa.workbench.kernel.messaging.DomainTopics
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TenantDomainEventsTest :
  StringSpec({
    "tenant domain events use tenant topic" {
      TenantDomainEvents.Created.type shouldBe "tenant.created"
      TenantDomainEvents.Created.topic shouldBe DomainTopics.TENANT
      TenantDomainEvents.DestroyRequested.type shouldBe "tenant.destroy_requested"
      TenantDomainEvents.Destroyed.type shouldBe "tenant.destroyed"
    }
  })
