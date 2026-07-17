package one.ztd.workbench.application.jobs.tenant

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.tenant.tenant.events.TenantDestroyedEvent
import one.ztd.workbench.tenant.tenant.events.TenantDomainEvents

class TenantDestroyedEventTest :
  StringSpec({
    "destroyed event spec references tenant topic" {
      TenantDomainEvents.Destroyed.type shouldBe "tenant.destroyed"
      TenantDestroyedEvent::class.simpleName shouldBe "TenantDestroyedEvent"
    }
  })
