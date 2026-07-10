package ink.doa.workbench.data.repository.tenantconfig

import ink.doa.workbench.core.tenantconfig.model.TenantConfigKey
import ink.doa.workbench.core.tenantconfig.model.UpsertTenantConfigCommand
import ink.doa.workbench.data.persistence.postgres.identity.TenantsTable
import ink.doa.workbench.data.support.withCorePostgresDatabase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ExposedTenantConfigRepositoryIntegrationTest :
  StringSpec({
    "tenant config entries can be upserted found and listed by tenant" {
      withCorePostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val repository = ExposedTenantConfigRepository(database)
        val key = TenantConfigKey("mail.smtp")

        val created =
          repository.upsert(
            UpsertTenantConfigCommand(
              tenantId = tenantId,
              key = key,
              value = JsonObject(mapOf("enabled" to JsonPrimitive(true))),
              secretRef = "secret://mail/smtp",
            )
          )

        created.key shouldBe key
        created.secretRef shouldBe "secret://mail/smtp"
        created.value.jsonObject["enabled"]?.jsonPrimitive?.boolean shouldBe true

        val updated =
          repository.upsert(
            UpsertTenantConfigCommand(
              tenantId = tenantId,
              key = key,
              value = JsonObject(mapOf("enabled" to JsonPrimitive(false))),
            )
          )

        updated.id shouldBe created.id
        updated.secretRef shouldBe null
        updated.value.jsonObject["enabled"]?.jsonPrimitive?.boolean shouldBe false
        repository.findByTenantAndKey(tenantId, key)?.id shouldBe created.id
        repository.listByTenant(tenantId).shouldHaveSize(1)
      }
    }
  })

private fun seedTenant(database: Database): UUID {
  val tenantId = UUID.randomUUID()
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  transaction(database) {
    TenantsTable.insert {
      it[id] = tenantId.toKotlinUuid()
      it[apiId] = "ten_${tenantId.toString().replace("-", "").take(12)}"
      it[name] = "Tenant Config Test"
      it[slug] = "tenant-config-${tenantId.toString().take(8)}"
      it[timezone] = "UTC"
      it[locale] = "en"
      it[createdAt] = now
      it[updatedAt] = now
    }
  }
  return tenantId
}
