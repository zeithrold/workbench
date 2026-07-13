package ink.doa.workbench.tenant.tenantconfig.model

import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@JvmInline
value class TenantConfigKey(val value: String) {
  init {
    require(value.matches(KEY_PATTERN)) {
      "Tenant config key must use lower snake case segments separated by dots."
    }
  }

  companion object {
    private val KEY_PATTERN = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
  }
}

data class TenantConfigRecord(
  val id: UUID,
  val tenantId: UUID,
  val key: TenantConfigKey,
  val value: JsonElement = JsonObject(emptyMap()),
  val secretRef: String? = null,
  val createdBy: UUID? = null,
  val updatedBy: UUID? = null,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class UpsertTenantConfigCommand(
  val tenantId: UUID,
  val key: TenantConfigKey,
  val value: JsonElement = JsonObject(emptyMap()),
  val secretRef: String? = null,
  val actorUserId: UUID? = null,
)

data class TenantConfigSpec<T : Any>(
  val key: TenantConfigKey,
  val serializer: KSerializer<T>,
  val defaultValue: T,
)
