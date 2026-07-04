package ink.doa.workbench.core.tenantconfig.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class TenantConfigRecordsTest :
  StringSpec({
    val now = OffsetDateTime.now(ZoneOffset.UTC)

    "tenant config key accepts dotted lower snake case" {
      TenantConfigKey("auth.session.ttl").value shouldBe "auth.session.ttl"
    }

    "tenant config key rejects invalid format" {
      shouldThrow<IllegalArgumentException> { TenantConfigKey("Auth.Session") }
      shouldThrow<IllegalArgumentException> { TenantConfigKey("auth") }
    }

    "tenant config record stores json value" {
      val record =
        TenantConfigRecord(
          id = UUID.randomUUID(),
          tenantId = UUID.randomUUID(),
          key = TenantConfigKey("auth.session.ttl"),
          value = JsonObject(mapOf("seconds" to JsonPrimitive(3600))),
          secretRef = null,
          createdBy = null,
          updatedBy = null,
          createdAt = now,
          updatedAt = now,
        )

      record.value shouldBe JsonObject(mapOf("seconds" to JsonPrimitive(3600)))
    }

    "upsert tenant config command carries actor" {
      val actorId = UUID.randomUUID()
      val command =
        UpsertTenantConfigCommand(
          tenantId = UUID.randomUUID(),
          key = TenantConfigKey("feature.flags"),
          actorUserId = actorId,
        )

      command.actorUserId shouldBe actorId
    }
  })
