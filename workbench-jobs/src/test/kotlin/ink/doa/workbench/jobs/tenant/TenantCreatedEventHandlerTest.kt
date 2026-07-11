package ink.doa.workbench.jobs.tenant

import ink.doa.workbench.core.tenant.events.TenantCreatedEvent
import io.kotest.core.spec.style.StringSpec
import kotlinx.coroutines.runBlocking

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
