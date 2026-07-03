package doa.ink.workbench.core.tenant.events

import doa.ink.workbench.core.identity.model.TenantRecord
import kotlinx.serialization.Serializable

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
