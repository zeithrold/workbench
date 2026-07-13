package ink.doa.workbench.tenant.tenantconfig

import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.tenant.tenantconfig.model.MailSmtpTenantConfig
import ink.doa.workbench.tenant.tenantconfig.model.TenantConfigKey
import ink.doa.workbench.tenant.tenantconfig.model.TenantConfigRecord
import ink.doa.workbench.tenant.tenantconfig.model.TenantConfigSpecs
import ink.doa.workbench.tenant.tenantconfig.model.UpsertTenantConfigCommand
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
          TenantConfigFixtures(
            tenantId = tenantId,
            key = TenantConfigSpecs.MailSmtp.key,
            value = JsonObject(mapOf("port" to JsonPrimitive("not-a-number"))),
          )
        )

      val error =
        shouldThrow<InvalidRequestException> {
          TenantConfigService(repository).get(tenantId, TenantConfigSpecs.MailSmtp)
        }
      error.errorCode shouldBe WorkbenchErrorCode.TENANT_CONFIG_DECODE_FAILED
      error.message shouldBe
        "Tenant config '${TenantConfigSpecs.MailSmtp.key.value}' cannot be decoded as '${TenantConfigSpecs.MailSmtp.key.value}'."
    }

    "list returns empty when tenant has no overrides" {
      val service = TenantConfigService(FakeTenantConfigRepository())

      service.list(UUID.randomUUID()) shouldBe emptyList()
    }

    "upsert forwards secretRef and actorUserId to the repository" {
      val repository = FakeTenantConfigRepository()
      val service = TenantConfigService(repository)
      val tenantId = UUID.randomUUID()
      val actorUserId = UUID.randomUUID()
      val config =
        MailSmtpTenantConfig(
          enabled = true,
          fromAddress = "noreply@example.test",
          host = "smtp.example.test",
          port = 587,
          username = "smtp-user",
          passwordSecretRef = "secret://mail/smtp",
        )

      service.upsert(
        tenantId,
        TenantConfigSpecs.MailSmtp,
        config,
        actorUserId = actorUserId,
        secretRef = "secret://mail/smtp",
      )

      val stored = requireNotNull(repository.lastUpsertCommand)
      stored.tenantId shouldBe tenantId
      stored.key shouldBe TenantConfigSpecs.MailSmtp.key
      stored.secretRef shouldBe "secret://mail/smtp"
      stored.actorUserId shouldBe actorUserId
    }

    "get returns spec default fields when no tenant override exists" {
      val service = TenantConfigService(FakeTenantConfigRepository())

      val result = service.get(UUID.randomUUID(), TenantConfigSpecs.MailSmtp)

      result.enabled shouldBe false
      result.port shouldBe 587
      result.host shouldBe null
    }

    "tenant config keys must use lower snake case dot separated segments" {
      TenantConfigKey("auth.session").value shouldBe "auth.session"

      shouldThrow<IllegalArgumentException> { TenantConfigKey("Auth.Session") }
      shouldThrow<IllegalArgumentException> { TenantConfigKey("auth") }
    }
  })

private class FakeTenantConfigRepository : TenantConfigRepository {
  val records = mutableMapOf<Pair<UUID, TenantConfigKey>, TenantConfigRecord>()
  var lastUpsertCommand: UpsertTenantConfigCommand? = null

  override suspend fun findByTenantAndKey(
    tenantId: UUID,
    key: TenantConfigKey,
  ): TenantConfigRecord? = records[tenantId to key]

  override suspend fun listByTenant(tenantId: UUID): List<TenantConfigRecord> =
    records.filterKeys { it.first == tenantId }.values.toList()

  override suspend fun upsert(command: UpsertTenantConfigCommand): TenantConfigRecord {
    lastUpsertCommand = command
    val existing = records[command.tenantId to command.key]
    val record =
      tenantConfigRecord(
        TenantConfigFixtures(
          id = existing?.id ?: UUID.randomUUID(),
          tenantId = command.tenantId,
          key = command.key,
          value = command.value,
          secretRef = command.secretRef,
          createdBy = existing?.createdBy ?: command.actorUserId,
          updatedBy = command.actorUserId,
        )
      )
    records[command.tenantId to command.key] = record
    return record
  }
}

private data class TenantConfigFixtures(
  val id: UUID = UUID.randomUUID(),
  val tenantId: UUID = UUID.randomUUID(),
  val key: TenantConfigKey = TenantConfigSpecs.MailSmtp.key,
  val value: kotlinx.serialization.json.JsonElement = JsonObject(emptyMap()),
  val secretRef: String? = null,
  val createdBy: UUID? = null,
  val updatedBy: UUID? = null,
)

private fun tenantConfigRecord(fixtures: TenantConfigFixtures) =
  TenantConfigRecord(
    id = fixtures.id,
    tenantId = fixtures.tenantId,
    key = fixtures.key,
    value = fixtures.value,
    secretRef = fixtures.secretRef,
    createdBy = fixtures.createdBy,
    updatedBy = fixtures.updatedBy,
    createdAt = OffsetDateTime.parse("2026-07-02T00:00:00Z"),
    updatedAt = OffsetDateTime.parse("2026-07-02T00:00:00Z"),
  )
