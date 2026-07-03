package doa.ink.workbench.core.tenant.events

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.identity.model.TenantRecord
import doa.ink.workbench.core.identity.model.TenantStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID

class TenantCreatedEventFromRecordTest :
  StringSpec({
    "maps tenant record to wire-safe event payload" {
      val createdAt = OffsetDateTime.parse("2026-07-03T09:15:30Z")
      val record =
        TenantRecord(
          id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
          apiId = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          slug = "acme",
          name = "Acme",
          status = TenantStatus.PENDING_ACTIVATION,
          createdAt = createdAt,
        )

      TenantCreatedEvent.from(record) shouldBe
        TenantCreatedEvent(
          tenantId = "ten_01JABCDEFGHJKMNPQRSTVWXYZ0",
          name = "Acme",
          status = "pending_activation",
          createdAt = createdAt.toString(),
        )
    }
  })
