package ink.doa.workbench.core.tenant.events

import ink.doa.workbench.core.identity.model.TenantRecord
import java.time.OffsetDateTime
import kotlinx.serialization.Serializable

@Serializable
data class TenantDestroyedEvent(
  val tenantId: String,
  val deletedAt: String,
  val deleteReason: String?,
) {
  companion object {
    fun from(
      tenant: TenantRecord,
      deletedAt: OffsetDateTime,
      deleteReason: String?,
    ): TenantDestroyedEvent =
      TenantDestroyedEvent(
        tenantId = tenant.apiId.value,
        deletedAt = deletedAt.toString(),
        deleteReason = deleteReason,
      )
  }
}
