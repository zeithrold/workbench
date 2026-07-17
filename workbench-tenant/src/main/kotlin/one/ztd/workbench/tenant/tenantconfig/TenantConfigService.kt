package one.ztd.workbench.tenant.tenantconfig

import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.tenant.tenantconfig.model.TenantConfigRecord
import one.ztd.workbench.tenant.tenantconfig.model.TenantConfigSpec
import one.ztd.workbench.tenant.tenantconfig.model.UpsertTenantConfigCommand
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
        WorkbenchErrorCode.TENANT_CONFIG_DECODE_FAILED,
        "Tenant config '${record.key.value}' cannot be decoded as '${spec.key.value}'.",
      )
    }
}
