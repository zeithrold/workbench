package ink.doa.workbench.tenant.tenantconfig

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.tenantconfig.TenantConfigRepository
import ink.doa.workbench.core.tenantconfig.model.MailSmtpTenantConfig
import ink.doa.workbench.core.tenantconfig.model.TenantConfigKey
import ink.doa.workbench.core.tenantconfig.model.TenantConfigRecord
import ink.doa.workbench.core.tenantconfig.model.TenantConfigSpecs
import ink.doa.workbench.core.tenantconfig.model.UpsertTenantConfigCommand
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class TenantConfigServiceTest :
  StringSpec({
    "get returns the spec default value when no tenant override exists" {
      val service = TenantConfigService(FakeTenantConfigRepository())

      val result = service.get(UUID.randomUUID(), TenantConfigSpecs.MailSmtp)

      result shouldBe MailSmtpTenantConfig()
    }

    "upsert stores typed config and get decodes it back" {
      val repository = FakeTenantConfigRepository()
      val service = TenantConfigService(repository)
      val tenantId = UUID.randomUUID()
      val config =
        MailSmtpTenantConfig(
          enabled = true,
          fromAddress = "noreply@example.test",
          host = "smtp.example.test",
          port = 2525,
          username = "smtp-user",
          passwordSecretRef = "secret://mail/smtp",
        )

      service.upsert(tenantId, TenantConfigSpecs.MailSmtp, config, actorUserId = UUID.randomUUID())

      service.get(tenantId, TenantConfigSpecs.MailSmtp) shouldBe config
      service.list(tenantId).shouldHaveSize(1)
    }

    "get rejects stored config that cannot be decoded for the requested spec" {
      val repository = FakeTenantConfigRepository()
      val tenantId = UUID.randomUUID()
      repository.records[tenantId to TenantConfigSpecs.MailSmtp.key] =
        tenantConfigRecord(
          tenantId = tenantId,
          key = TenantConfigSpecs.MailSmtp.key,
          value = JsonObject(mapOf("port" to JsonPrimitive("not-a-number"))),
        )

      shouldThrow<InvalidRequestException> {
        TenantConfigService(repository).get(tenantId, TenantConfigSpecs.MailSmtp)
      }
    }

    "tenant config keys must use lower snake case dot separated segments" {
      TenantConfigKey("auth.session").value shouldBe "auth.session"

      shouldThrow<IllegalArgumentException> { TenantConfigKey("Auth.Session") }
      shouldThrow<IllegalArgumentException> { TenantConfigKey("auth") }
    }
  })

private class FakeTenantConfigRepository : TenantConfigRepository {
  val records = mutableMapOf<Pair<UUID, TenantConfigKey>, TenantConfigRecord>()

  override suspend fun findByTenantAndKey(
    tenantId: UUID,
    key: TenantConfigKey,
  ): TenantConfigRecord? = records[tenantId to key]

  override suspend fun listByTenant(tenantId: UUID): List<TenantConfigRecord> =
    records.filterKeys { it.first == tenantId }.values.toList()

  override suspend fun upsert(command: UpsertTenantConfigCommand): TenantConfigRecord {
    val existing = records[command.tenantId to command.key]
    val record =
      tenantConfigRecord(
        id = existing?.id ?: UUID.randomUUID(),
        tenantId = command.tenantId,
        key = command.key,
        value = command.value,
        secretRef = command.secretRef,
        createdBy = existing?.createdBy ?: command.actorUserId,
        updatedBy = command.actorUserId,
      )
    records[command.tenantId to command.key] = record
    return record
  }
}

private fun tenantConfigRecord(
  id: UUID = UUID.randomUUID(),
  tenantId: UUID = UUID.randomUUID(),
  key: TenantConfigKey = TenantConfigSpecs.MailSmtp.key,
  value: kotlinx.serialization.json.JsonElement = JsonObject(emptyMap()),
  secretRef: String? = null,
  createdBy: UUID? = null,
  updatedBy: UUID? = null,
) =
  TenantConfigRecord(
    id = id,
    tenantId = tenantId,
    key = key,
    value = value,
    secretRef = secretRef,
    createdBy = createdBy,
    updatedBy = updatedBy,
    createdAt = OffsetDateTime.parse("2026-07-02T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-07-02T00:00:00Z"),
  )
