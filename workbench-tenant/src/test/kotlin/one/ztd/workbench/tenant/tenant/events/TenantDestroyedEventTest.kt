package one.ztd.workbench.tenant.tenant.events

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.tenant.model.TenantRecord

class TenantDestroyedEventTest :
  StringSpec({
    "from maps tenant deletion metadata" {
      val deletedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z")
      val tenant =
        TenantRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("ten"),
          slug = "acme",
          name = "Acme",
          timezone = "UTC",
          locale = "en-US",
          createdAt = deletedAt,
          updatedAt = deletedAt,
        )

      TenantDestroyedEvent.from(
          tenant = tenant,
          deletedAt = deletedAt,
          deleteReason = "cleanup",
        )
        .tenantId shouldBe tenant.apiId.value
    }
  })
