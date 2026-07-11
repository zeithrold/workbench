package ink.doa.workbench.jobs.tenant

import ink.doa.workbench.core.tenant.events.TenantDestroyedEvent
import ink.doa.workbench.core.tenant.events.TenantDomainEvents
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TenantDestroyedEventTest :
  StringSpec({
    "destroyed event spec references tenant topic" {
      TenantDomainEvents.Destroyed.type shouldBe "tenant.destroyed"
      TenantDestroyedEvent::class.simpleName shouldBe "TenantDestroyedEvent"
    }
  })
