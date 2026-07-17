package one.ztd.workbench.tenant.tenant.events

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.tenant.model.TenantRecord
import one.ztd.workbench.tenant.model.TenantStatus

class TenantDestroyRequestedEventFromRecordTest :
  StringSpec({
    "maps tenant record to wire-safe destroy requested payload" {
      val requestedAt = OffsetDateTime.parse("2026-07-03T09:15:30Z")
      val record =
        TenantRecord(
          id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
          apiId = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          slug = "acme",
          name = "Acme",
          status = TenantStatus.DESTROYING,
          createdAt = requestedAt,
        )
      val requestedBy = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1")

      TenantDestroyRequestedEvent.from(
        tenant = record,
        deleteReason = "Customer churned",
        requestedAt = requestedAt,
        requestedByPublicId = requestedBy,
      ) shouldBe
        TenantDestroyRequestedEvent(
          tenantId = "ten_01JABCDEFGHJKMNPQRSTVWXYZ0",
          requestedBy = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
          deleteReason = "Customer churned",
          requestedAt = requestedAt.toString(),
        )
    }

    "maps null delete reason" {
      val requestedAt = OffsetDateTime.parse("2026-07-03T09:15:30Z")
      val record =
        TenantRecord(
          id = UUID.randomUUID(),
          apiId = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          slug = "acme",
          name = "Acme",
        )

      TenantDestroyRequestedEvent.from(
          tenant = record,
          deleteReason = null,
          requestedAt = requestedAt,
          requestedByPublicId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
        )
        .deleteReason
        .shouldBe(null)
    }
  })
