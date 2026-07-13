package ink.doa.workbench.application.jobs.tenant

import ink.doa.workbench.tenant.tenant.events.TenantDestroyedEvent
import ink.doa.workbench.tenant.tenant.events.TenantDomainEvents
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TenantDestroyedEventTest :
  StringSpec({
    "destroyed event spec references tenant topic" {
      TenantDomainEvents.Destroyed.type shouldBe "tenant.destroyed"
      TenantDestroyedEvent::class.simpleName shouldBe "TenantDestroyedEvent"
    }
  })
