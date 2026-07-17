package one.ztd.workbench.tenant.tenant.events

import kotlinx.serialization.Serializable
import one.ztd.workbench.tenant.model.TenantRecord

@Serializable
data class TenantCreatedEvent(
  val tenantId: String,
  val name: String,
  val status: String,
  val createdAt: String,
) {
  companion object {
    fun from(record: TenantRecord): TenantCreatedEvent =
      TenantCreatedEvent(
        tenantId = record.apiId.value,
        name = record.name,
        status = record.status.name.lowercase(),
        createdAt = record.createdAt?.toString() ?: error("Tenant record is missing createdAt."),
      )
  }
}
