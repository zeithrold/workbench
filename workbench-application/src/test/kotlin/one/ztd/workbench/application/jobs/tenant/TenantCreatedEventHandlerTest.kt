package one.ztd.workbench.application.jobs.tenant

import io.kotest.core.spec.style.StringSpec
import kotlinx.coroutines.runBlocking
import one.ztd.workbench.tenant.tenant.events.TenantCreatedEvent

class TenantCreatedEventHandlerTest :
  StringSpec({
    "handle logs tenant created payload" {
      val handler = TenantCreatedEventHandler()

      runBlocking {
        handler.handle(
          TenantCreatedEvent(
            tenantId = "ten_abc",
            name = "Acme",
            status = "active",
            createdAt = "2026-07-04T00:00:00Z",
          )
        )
      }
    }
  })
