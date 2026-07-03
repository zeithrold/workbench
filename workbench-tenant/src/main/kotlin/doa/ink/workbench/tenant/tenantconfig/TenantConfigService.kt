package doa.ink.workbench.tenant.tenantconfig

import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.tenantconfig.TenantConfigRepository
import doa.ink.workbench.core.tenantconfig.model.TenantConfigRecord
import doa.ink.workbench.core.tenantconfig.model.TenantConfigSpec
import doa.ink.workbench.core.tenantconfig.model.UpsertTenantConfigCommand
import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.springframework.stereotype.Service

@Service
class TenantConfigService(private val repository: TenantConfigRepository) {
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  suspend fun <T : Any> get(tenantId: UUID, spec: TenantConfigSpec<T>): T {
    val record = repository.findByTenantAndKey(tenantId, spec.key) ?: return spec.defaultValue
    return decode(spec, record)
  }

  suspend fun list(tenantId: UUID): List<TenantConfigRecord> = repository.listByTenant(tenantId)

  suspend fun <T : Any> upsert(
    tenantId: UUID,
    spec: TenantConfigSpec<T>,
    value: T,
    actorUserId: UUID? = null,
    secretRef: String? = null,
  ): TenantConfigRecord =
    repository.upsert(
      UpsertTenantConfigCommand(
        tenantId = tenantId,
        key = spec.key,
        value = json.encodeToJsonElement(spec.serializer, value),
        secretRef = secretRef,
        actorUserId = actorUserId,
      )
    )

  private fun <T : Any> decode(spec: TenantConfigSpec<T>, record: TenantConfigRecord): T =
    try {
      json.decodeFromJsonElement(spec.serializer, record.value)
    } catch (_: SerializationException) {
      throw InvalidRequestException(
        "Tenant config '${record.key.value}' cannot be decoded as '${spec.key.value}'."
      )
    }
}
